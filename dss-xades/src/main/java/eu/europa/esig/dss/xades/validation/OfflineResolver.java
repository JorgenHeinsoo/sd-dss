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
package eu.europa.esig.dss.xades.validation;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.xml.security.Init;
import org.apache.xml.security.signature.XMLSignatureInput;
import org.apache.xml.security.utils.resolver.ResourceResolverContext;
import org.apache.xml.security.utils.resolver.ResourceResolverException;
import org.apache.xml.security.utils.resolver.ResourceResolverSpi;
import org.apache.xml.utils.URI;
import org.digidoc4j.dss.xades.BDocTmSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;

import eu.europa.esig.dss.DSSDocument;
import eu.europa.esig.dss.MimeType;

/**
 * This class helps us home users to resolve http URIs without a network connection
 *
 */
public class OfflineResolver extends ResourceResolverSpi {

	private static final Logger LOG = LoggerFactory.getLogger(OfflineResolver.class);

	private final List<DSSDocument> documents;

	static {

		Init.init();
	}

	public OfflineResolver(final List<DSSDocument> documents) {

		this.documents = documents;
	}

	@Override
	public boolean engineCanResolveURI(final ResourceResolverContext context) {
		final Attr uriAttr = context.attr;
		if (uriAttr != null) {
			String encodedDocumentUri = uriAttr.getNodeValue();
			String documentUri = decodeUrl(encodedDocumentUri);
			if ("".equals(documentUri) || documentUri.startsWith("#")) {
				return false;
			}
			if (isKnown(documentUri) != null) {
				LOG.debug("I state that I can resolve '" + documentUri.toString() + "' (external document)");
				return true;
			}
			documentUri = BDocTmSupport.fixEncoding(encodedDocumentUri);
			documentUri = decodeUrl(documentUri);
			if (isKnown(documentUri) != null) {
				LOG.debug("I state that I can resolve '" + documentUri.toString() + "' (external document)");
				return true;
			}
			try {
				final String baseUriString = context.baseUri;
				if (StringUtils.isNotEmpty(baseUriString)) {
					final URI baseUri = new URI(baseUriString);
					URI uriNew = new URI(baseUri, documentUri);
					if (uriNew.getScheme().equals("http")) {
						LOG.debug("I state that I can resolve '" + uriNew.toString() + "'");
						return true;
					}
					LOG.debug("I state that I can't resolve '" + uriNew.toString() + "'");
				}
			} catch (URI.MalformedURIException ex) {
				if (documents == null || documents.size() == 0) {
					LOG.warn("OfflineResolver: WARNING: ", ex);
				}
			}
		} else if (doesContainOnlyOneDocument()) { // no URI is allowed in ASiC-S with one file
			return true;
		}
		return false;
	}

	@Override
	public XMLSignatureInput engineResolveURI(ResourceResolverContext context) throws ResourceResolverException {

		final Attr uriAttr = context.attr;
		String documentUri = null;
		if (uriAttr == null && doesContainOnlyOneDocument()) {
			documentUri = "";
		} else if (uriAttr != null) {
			documentUri = uriAttr.getNodeValue();
		}
		String decodedDocumentUri = decodeUrl(documentUri);
		DSSDocument document = isKnown(decodedDocumentUri);
		if(document == null) {
			decodedDocumentUri = BDocTmSupport.fixEncoding(documentUri);
			decodedDocumentUri = decodeUrl(decodedDocumentUri);
			document = isKnown(documentUri);
		}
		if(document == null) {
			document = getDocument(decodedDocumentUri);
		}
		if (document != null) {

			// The input stream is closed automatically by XMLSignatureInput class

			// TODO-Bob (05/09/2014): There is an error concerning the input streams base64 encoded. Some extra bytes
			// are added within the santuario which breaks the HASH.
			// TODO-Vin (05/09/2014): Can you create an isolated test-case JIRA DSS-?
			InputStream inputStream = document.openStream();
			final XMLSignatureInput result = new XMLSignatureInput(inputStream);
			result.setSourceURI(decodedDocumentUri);
			final MimeType mimeType = document.getMimeType();
			if (mimeType != null) {
				result.setMIMEType(mimeType.getMimeTypeString());
			}
			return result;
		} else {
			Object exArgs[] = { "The uriNodeValue " + decodedDocumentUri + " is not configured for offline work" };
			throw new ResourceResolverException("generic.EmptyMessage", exArgs, decodedDocumentUri, context.baseUri);
		}
	}

	private DSSDocument isKnown(final String documentUri) {

		for (final DSSDocument dssDocument : documents) {

			if (isRightDocument(documentUri, dssDocument)) {

				return dssDocument;
			}
			DSSDocument nextDssDocument = dssDocument.getNextDocument();
			while (nextDssDocument != null) {

				if (isRightDocument(documentUri, nextDssDocument)) {
					return nextDssDocument;
				}
				nextDssDocument = nextDssDocument.getNextDocument();
			}
		}
		return null;
	}

	private static boolean isRightDocument(final String documentUri, final DSSDocument document) {

		final String documentUri_ = document.getName();
		if (documentUri.equals(documentUri_)) {

			return true;
		}
		final int length = documentUri.length();
		final int length_ = documentUri_.length();
		// For the file name as "/toto.txt"
		final boolean case1 = documentUri.startsWith("/") && length - 1 == length_;
		// For the file name as "./toto.txt"
		final boolean case2 = documentUri.startsWith("./") && length - 2 == length_;
		if (documentUri.endsWith(documentUri_) && (case1 || case2)) {

			return true;
		}
		return false;
	}

	private DSSDocument getDocument(final String documentUri) {

		final DSSDocument document = isKnown(documentUri);
		if (document != null) {
			return document;
		}
		if (doesContainOnlyOneDocument()) {

			return documents.get(0);
		}
		return null;
	}

	private boolean doesContainOnlyOneDocument() {

		return documents != null && documents.size() == 1;
	}

	private String decodeUrl(String documentUri) {
		try {
			return URLDecoder.decode(documentUri, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			LOG.error("Unable to decode '" + documentUri + "' : " + e.getMessage(), e);
		}
		return documentUri;
	}
}