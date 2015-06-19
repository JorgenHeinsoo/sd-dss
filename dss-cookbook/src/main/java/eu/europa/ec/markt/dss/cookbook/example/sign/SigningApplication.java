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
package eu.europa.ec.markt.dss.cookbook.example.sign;

import java.io.IOException;
import java.util.Date;

import eu.europa.ec.markt.dss.DSSUtils;
import eu.europa.ec.markt.dss.cookbook.sources.JavaKeyStoreTool;
import eu.europa.ec.markt.dss.parameter.SignatureParameters;
import eu.europa.ec.markt.dss.signature.DSSDocument;
import eu.europa.ec.markt.dss.signature.FileDocument;
import eu.europa.ec.markt.dss.signature.SignatureLevel;
import eu.europa.ec.markt.dss.signature.SignaturePackaging;
import eu.europa.ec.markt.dss.signature.token.JKSSignatureToken;
import eu.europa.ec.markt.dss.signature.token.KSPrivateKeyEntry;
import eu.europa.ec.markt.dss.signature.xades.XAdESService;
import eu.europa.ec.markt.dss.validation102853.CommonCertificateVerifier;

public class SigningApplication {

	public static void main(String[] args) throws IOException {

		//GET THE LOCATION OF YOUR JKS FILE
		String location = "yourFile.jks";
		JavaKeyStoreTool jks = new JavaKeyStoreTool(location, "password");

		JKSSignatureToken signingToken = new JKSSignatureToken(location, "password");

		KSPrivateKeyEntry privateKey = jks.getPrivateKey("dss", "password");

		DSSDocument toBeSigned = new FileDocument("src/test/resources/xml_example.xml");

		SignatureParameters params = new SignatureParameters();

		params.setSignatureLevel(SignatureLevel.XAdES_BASELINE_B);
		params.setSignaturePackaging(SignaturePackaging.ENVELOPED);
		params.setSigningCertificate(privateKey.getCertificate());
		params.setCertificateChain(privateKey.getCertificateChain());
		params.bLevel().setSigningDate(new Date());

		CommonCertificateVerifier commonCertificateVerifier = new CommonCertificateVerifier();
		XAdESService service = new XAdESService(commonCertificateVerifier);
		byte[] dataToSign = service.getDataToSign(toBeSigned, params);
		byte[] signatureValue = signingToken.sign(dataToSign, params.getDigestAlgorithm(), privateKey);
		DSSDocument signedDocument = service.signDocument(toBeSigned, params, signatureValue);
		DSSUtils.copy(signedDocument.openStream(), System.out);
	}
}
