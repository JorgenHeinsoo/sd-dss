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
package eu.europa.ec.markt.dss.signature.pdf;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.SignatureException;
import java.util.Map;

import eu.europa.ec.markt.dss.DigestAlgorithm;
import eu.europa.ec.markt.dss.exception.DSSException;
import eu.europa.ec.markt.dss.parameter.SignatureParameters;
import eu.europa.ec.markt.dss.validation102853.CertificatePool;

/**
 * The usage of this interface permits the user to choose the underlying PDF library use to created PDF signatures.
 *
 *
 */
public interface PDFSignatureService {

  /**
   * Returns the digest value of a PDF document
   *
   * @param toSignDocument
   * @param parameters
   * @param digestAlgorithm
   * @param extraDictionariesToAddBeforeSign only in the case of timestamp
   * @return
   * @throws DSSException
   */
  byte[] digest(final InputStream toSignDocument, final SignatureParameters parameters,
                final DigestAlgorithm digestAlgorithm,
                final Map.Entry<String, PdfDict>... extraDictionariesToAddBeforeSign) throws DSSException;

  /**
   * Signs a PDF document
   *
   * @param pdfData
   * @param signatureValue
   * @param signedStream
   * @param parameters
   * @param digestAlgorithm
   * @param extraDictionariesToAddBeforeSign
   * @throws DSSException
   */
  void sign(final InputStream pdfData, final byte[] signatureValue, final OutputStream signedStream,
            final SignatureParameters parameters, final DigestAlgorithm digestAlgorithm,
            final Map.Entry<String, PdfDict>... extraDictionariesToAddBeforeSign) throws DSSException;

  /**
   * Retrieves and triggers validation of the signatures from a PDF document
   *
   * @param validationCertPool
   * @param input
   * @param callback
   * @throws DSSException
   * @throws SignatureException
   */
  void validateSignatures(final CertificatePool validationCertPool, final InputStream input,
                          final SignatureValidationCallback callback) throws DSSException;

}