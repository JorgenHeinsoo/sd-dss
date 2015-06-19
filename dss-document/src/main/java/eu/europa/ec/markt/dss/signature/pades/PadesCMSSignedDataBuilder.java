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
package eu.europa.ec.markt.dss.signature.pades;

import java.util.Map;

import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.cms.CMSAttributeTableGenerationException;
import org.bouncycastle.cms.CMSAttributeTableGenerator;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.SignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;

import eu.europa.ec.markt.dss.exception.DSSException;
import eu.europa.ec.markt.dss.parameter.SignatureParameters;
import eu.europa.ec.markt.dss.signature.cades.CAdESLevelBaselineB;
import eu.europa.ec.markt.dss.signature.cades.CMSSignedDataBuilder;
import eu.europa.ec.markt.dss.validation102853.CertificateVerifier;

/**
 * TODO
 *
 *
 *
 *
 *
 *
 */
class PadesCMSSignedDataBuilder extends CMSSignedDataBuilder {

	/**
	 * This is the default constructor for {@code CMSSignedDataGeneratorBuilder}. The {@code CertificateVerifier} is used to find the trusted certificates.
	 *
	 * @param certificateVerifier {@code CertificateVerifier} provides information on the sources to be used in the validation process in the context of a signature.
	 */
	public PadesCMSSignedDataBuilder(CertificateVerifier certificateVerifier) {
		super(certificateVerifier);
	}

	@Override
	protected CMSSignedDataGenerator createCMSSignedDataGenerator(SignatureParameters parameters, ContentSigner contentSigner, SignerInfoGeneratorBuilder signerInfoGeneratorBuilder,
	                                                              CMSSignedData originalSignedData) throws DSSException {

		return super.createCMSSignedDataGenerator(parameters, contentSigner, signerInfoGeneratorBuilder, originalSignedData);
	}

	/**
	 * @param parameters the parameters of the signature containing values for the attributes
	 * @return a SignerInfoGeneratorBuilder that generate the signed and unsigned attributes according to the CAdESLevelBaselineB and
	 * PAdESLevelBaselineB
	 */
	protected SignerInfoGeneratorBuilder getSignerInfoGeneratorBuilder(final SignatureParameters parameters, final byte[] messageDigest) {

		final CAdESLevelBaselineB cAdESLevelBaselineB = new CAdESLevelBaselineB(true);
		final PAdESLevelBaselineB pAdESProfileEPES = new PAdESLevelBaselineB();

		final DigestCalculatorProvider digestCalculatorProvider = new BcDigestCalculatorProvider();

		SignerInfoGeneratorBuilder signerInfoGeneratorBuilder = new SignerInfoGeneratorBuilder(digestCalculatorProvider);
		signerInfoGeneratorBuilder = signerInfoGeneratorBuilder.setSignedAttributeGenerator(new CMSAttributeTableGenerator() {

			@SuppressWarnings("unchecked")
			@Override
			public AttributeTable getAttributes(@SuppressWarnings("rawtypes") Map params) throws CMSAttributeTableGenerationException {

				return pAdESProfileEPES.getSignedAttributes(params, cAdESLevelBaselineB, parameters, messageDigest);
			}
		});

		signerInfoGeneratorBuilder.setUnsignedAttributeGenerator(new CMSAttributeTableGenerator() {
			@Override
			public AttributeTable getAttributes(Map params) throws CMSAttributeTableGenerationException {
				return pAdESProfileEPES.getUnsignedAttributes();
			}
		});

		return signerInfoGeneratorBuilder;
	}
}
