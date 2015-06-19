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
package eu.europa.ec.markt.dss.signature;

import java.io.Serializable;

import eu.europa.ec.markt.dss.exception.DSSException;
import eu.europa.ec.markt.dss.parameter.SignatureParameters;
import eu.europa.ec.markt.dss.validation102853.tsp.TSPSource;

/**
 * This interface {@code DocumentSignatureService} provides operations for the signature creation and for its extension.
 *
 *
 */
public interface DocumentSignatureService extends Serializable {

	/**
	 * Retrieves the bytes of the data that need to be signed based on the {@code toSignDocument} and {@code parameters}. (Added in version 4) When
	 * {@code toSignDocument} contains an already existing signature the returned bytes are related to a new parallel signature.
	 *
	 * - Enveloped signature (XML): a new signature is added and the signed data corresponds to that pointed by the first signature;
	 *
	 * - Enveloping signature:
	 *
	 * - - XML: The parallel signature is not possible
	 *
	 * - - CMS: A new parallel signature is added
	 *
	 * - Detached signature:
	 *
	 * - - XML: The parallel signature is added
	 *
	 * - - CMS: A new parallel signature is added
	 *
	 *
	 * @param toSignDocument
	 *            document to sign or the already existing signature
	 * @param parameters
	 *            set of the driving signing parameters
	 * @return
	 * @throws DSSException
	 */
	byte[] getDataToSign(final DSSDocument toSignDocument, final SignatureParameters parameters) throws DSSException;

	/**
	 * Signs the toSignDocument with the provided signatureValue.
	 *
	 * @param toSignDocument
	 *            document to sign
	 * @param parameters
	 *            set of the driving signing parameters
	 * @param signatureValue
	 * @return
	 * @throws DSSException
	 */
	DSSDocument signDocument(final DSSDocument toSignDocument, final SignatureParameters parameters, final byte[] signatureValue) throws DSSException;

	/**
	 * Signs the toSignDocument in the single operation. It is possible to invoke this method when the private key is known on the server side or
	 * everything is done on the client side.
	 *
	 * @param toSignDocument
	 *            document to sign
	 * @param parameters
	 *            set of the driving signing parameters
	 * @return
	 * @throws DSSException
	 */
	@Deprecated
	DSSDocument signDocument(final DSSDocument toSignDocument, final SignatureParameters parameters) throws DSSException;

	/**
	 * Extends the level of the signatures in the {@code toExtendDocument}
	 *
	 * @param toExtendDocument
	 *            document to extend
	 * @param parameters
	 *            set of the driving signing parameters
	 * @return
	 * @throws DSSException
	 */
	DSSDocument extendDocument(final DSSDocument toExtendDocument, final SignatureParameters parameters) throws DSSException;

	/**
	 * This setter allows to define the TSP (timestamp provider) source.
	 *
	 * @param tspSource
	 *            The time stamp source which is used when timestamping the signature.
	 */
	void setTspSource(final TSPSource tspSource);
}