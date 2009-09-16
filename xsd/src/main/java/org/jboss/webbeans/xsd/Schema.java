/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,  
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.webbeans.xsd;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.StandardLocation;
import javax.xml.stream.events.Namespace;

import org.jboss.webbeans.xsd.model.ClassModel;
import org.jboss.webbeans.xsd.model.TypedModel;

import com.sun.org.apache.xml.internal.serialize.OutputFormat;

/**
 * A schema representation
 * 
 * @author Nicklas Karlsson
 * 
 */
public class Schema
{
   // The default namespace of the schema
   public static final List<Namespace> defaultNamespaces = new ArrayList<Namespace>();

   static
   {
      defaultNamespaces.add(new Namespace("wb", "http://seamframework.org/WebBeans"));
      defaultNamespaces.add(new Namespace("xsi", "http://www.w3.org/2001/XMLSchema-instance"));
   };

   // The name of the package
   private String packageName;
   // The XSD document
   private Document document;
   // The namespace handler
   private NamespaceHandler namespaceHandler;
   // The set of classes to update
   private Set<ClassModel> classModels;
   private PackageElement packageElement;

   /**
    * Creates a new package
    * 
    * @param packageName The name of the package
    * @throws DocumentException 
    */
   public Schema(String packageName, PackageElement packageElement, Filer filer) throws DocumentException
   {
      this.packageName = packageName;
      this.packageElement = packageElement;
      classModels = new HashSet<ClassModel>();
      namespaceHandler = new NamespaceHandler(packageName);
      try
      {
         document = readDocument(filer);
      } catch (IOException e) {
         // schema not found, safe to proceed
      }
      if (document == null)
      {
         document = createDocument();
      }
      init();
   }

   /**
    * Creates a new schema document
    * 
    * @return The document
    */
   private Document createDocument()
   {
      Document document = DocumentHelper.createDocument();
      QName rootQName = DocumentFactory.getInstance().createQName("schema", "xs", "http://www.w3.org/2001/XMLSchema");
      Element rootElement = DocumentFactory.getInstance().createElement(rootQName);
      rootElement.addAttribute("elementFormDefault", "qualified");
      rootElement.addAttribute("targetNamespace", "urn:java:" + packageName);
      rootElement.addAttribute("elementFormDefault", "qualified");
      rootElement.addAttribute("xsi:schemaLocation", "http://www.w3.org/2001/XMLSchema http://www.w3.org/2001/XMLSchema.xsd");
      document.setRootElement(rootElement);
      for (Namespace namespace : defaultNamespaces)
      {
         rootElement.add(namespace);
      }
      return document;
   }

   /**
    * Static factory method
    * 
    * @param packageName The package name
    * @param packageElement The package content representation
    * @param filer The filer for resources
    * 
    * @return A new schema instance
    * 
    * @throws DocumentException If an existing schema could not be parsed
    */
   public static Schema of(String packageName, PackageElement packageElement, Filer filer) throws DocumentException
   {
      return new Schema(packageName, packageElement, filer);
   }

   /**
    * Reads a schema document
    * 
    * @param filer The filer to be used for resource reading
    * 
    * @return The schema documetn
    * 
    * @throws IOException If the file could not be find or there was an error reading it
    * @throws DocumentException If the read document could not be parsed
    */
   private Document readDocument(Filer filer) throws IOException, DocumentException
   {
      InputStream in = filer.getResource(StandardLocation.CLASS_OUTPUT, packageName, "schema.xsd").openInputStream();
      return new SAXReader().read(in);
   }

   /**
    * Writes a schema back to disk
    * 
    * @param filer The filer to be used for resource writing
    * 
    * @throws IOException If the file could not be written
    */
   public void write(Filer filer) throws IOException
   {
      OutputFormat format = OutputFormat.createPrettyPrint();
      OutputStream out = filer.createResource(StandardLocation.CLASS_OUTPUT, packageName, "schema.xsd").openOutputStream();
      XMLWriter writer = new XMLWriter(out, format);
      writer.write(document);
      writer.flush();
      writer.close();
   }

   /**
    * Adds a class model to the working set and adds the referenced types to the
    * namespace handler
    * 
    * @param classModel The class model
    */
   public void addClass(ClassModel classModel)
   {
      classModels.add(classModel);
      for (TypedModel reference : classModel.getTypeReferences())
      {
         namespaceHandler.addPackage(reference.getTypePackage());
      }
   }

   /**
    * Cleans out namespaces and XSD for files that are no longer present in the
    * package
    */
   private void init()
   {
      // Removes elements that are no longer in the package
      for (Object xsdClass : document.selectNodes("/xs:schema/xs:element"))
      {
         String FQN = packageName + "." + ((Element) xsdClass).attributeValue("name");
         if (!isClassInPackage(FQN))
         {
            ((Element) xsdClass).detach();
         }
      }

      // Collect namespaces that are references in the document
      Set<Namespace> referencedNamespaces = new HashSet<Namespace>(defaultNamespaces);
      for (Object attribute : document.getRootElement().selectNodes("//@type"))
      {
         String ref = ((Attribute) attribute).getValue();
         int colon = ref.indexOf(":");
         String prefix = colon < 0 ? "" : ref.substring(0, colon);
         referencedNamespaces.add(document.getRootElement().getNamespaceForPrefix(prefix));
      }

      // Register the namespaces with the namespace handler if they are present, otherwise
      // remove them from the document
      for (Object item : document.getRootElement().additionalNamespaces())
      {
         Namespace namespace = (Namespace) item;
         if (referencedNamespaces.contains(namespace))
         {
            namespaceHandler.addNamespace(namespace);
         }
         else
         {
            document.getRootElement().remove(namespace);
         }
      }
   }

   /**
    * Checks if a class is still in the package
    * 
    * @param packageElement The package abstraction
    * @param FQN The full name of the class
    * @return True if present, false otherwise
    */
   private boolean isClassInPackage(String FQN)
   {
      for (javax.lang.model.element.Element classElement : packageElement.getEnclosedElements())
      {
         TypeElement typeElement = (TypeElement) classElement;
         if (typeElement.getQualifiedName().toString().equals(FQN))
         {
            return true;
         }
      }
      return false;
   }

   /**
    * Rebuilds the schema document
    * 
    * @param packageElement The package abstraction
    * @return the schema for fluent interface
    */
   public Schema rebuild()
   {
      // Adds new namespaces if they are not already present
      for (Namespace namespace : namespaceHandler.getNamespaces())
      {
         if (document.getRootElement().getNamespaceForPrefix(namespace.getPrefix()) == null)
         {
            document.getRootElement().add(namespace);
         }
      }

      // Remove old version of class xsd (if present) and add the fresh version
      for (ClassModel classModel : classModels)
      {
         for (Object previousClass : document.selectNodes("/xs:schema/xs:element[@name=\"" + classModel.getSimpleName() + "\"]"))
         {
            ((Element) previousClass).detach();
         }
         document.getRootElement().add(classModel.toXSD(namespaceHandler));
      }
      return this;
   }

   @Override
   public String toString()
   {
      StringBuilder buffer = new StringBuilder();
      buffer.append("Package: " + packageName + "\n");
      buffer.append("Used namespaces\n");
      for (Namespace namespace : namespaceHandler.getNamespaces())
      {
         buffer.append("  " + namespace + "\n");
      }
      buffer.append("Contained classes:\n");
      for (ClassModel classModel : classModels)
      {
         buffer.append(classModel + "\n");
      }
      return buffer.toString();
   }

}
