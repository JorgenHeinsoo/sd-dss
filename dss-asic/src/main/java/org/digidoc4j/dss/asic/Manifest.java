/**
 * DSS - Digital Signature Services
 * Copyright (C) 2015 European Commission, provided under the CEF programme
 *
 * This file is part of the "DSS - Digital Signature Services" project.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.digidoc4j.dss.asic;

import eu.europa.esig.dss.DSSDocument;
import eu.europa.esig.dss.DSSException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.OutputStream;

/**
 * Represents the META-INF/manifest.xml subdocument
 */
public class Manifest {

  public static final String XML_PATH = "META-INF/manifest.xml";

  private Document dom;
  private final Logger LOG = LoggerFactory.getLogger(Manifest.class);
  private Element rootElement;

  /**
   * creates object to create manifest files
   */
  public Manifest() {
    LOG.debug("Creating new manifest");
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    try {
      DocumentBuilder db = dbf.newDocumentBuilder();

      dom = db.newDocument();
      rootElement = dom.createElement("manifest:manifest");
      rootElement.setAttribute("xmlns:manifest", "urn:oasis:names:tc:opendocument:xmlns:manifest:1.0");

      Element firstChild = dom.createElement("manifest:file-entry");
      firstChild.setAttribute("manifest:full-path", "/");
      firstChild.setAttribute("manifest:media-type", "application/vnd.etsi.asic-e+zip");
      rootElement.appendChild(firstChild);

      dom.appendChild(rootElement);

    } catch (ParserConfigurationException e) {
      LOG.error(e.getMessage());
      throw new DSSException(e);
    }
  }

  /**
   * adds list of attachments to create manifest file
   *
   * @param document list of data files
   */
  public void addFileEntry(DSSDocument document) {
    Element childElement;
    DSSDocument entry = document;
    do  {
      childElement = dom.createElement("manifest:file-entry");
      childElement.setAttribute("manifest:media-type", entry.getMimeType().getMimeTypeString());
      childElement.setAttribute("manifest:full-path", entry.getName());
      rootElement.appendChild(childElement);
      LOG.debug("adds " + entry.getName() + " to manifest");
      entry = entry.getNextDocument();
    } while (entry != null);

  }

  /**
   * sends manifest files to output stream
   *
   * @param out output stream
   */
  public void save(OutputStream out) {
    DOMImplementationLS implementation = (DOMImplementationLS) dom.getImplementation();
    LSOutput lsOutput = implementation.createLSOutput();
    lsOutput.setByteStream(out);
    LSSerializer writer = implementation.createLSSerializer();
    writer.write(dom, lsOutput);
  }
}
