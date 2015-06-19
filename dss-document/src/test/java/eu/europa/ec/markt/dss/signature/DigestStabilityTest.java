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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.security.MessageDigest;
import java.util.Date;

import org.apache.commons.codec.binary.Base64;
import org.junit.Test;

import eu.europa.ec.markt.dss.DigestAlgorithm;
import eu.europa.ec.markt.dss.SignatureAlgorithm;
import eu.europa.ec.markt.dss.parameter.SignatureParameters;
import eu.europa.ec.markt.dss.service.CertificateService;
import eu.europa.ec.markt.dss.signature.pades.PAdESService;
import eu.europa.ec.markt.dss.signature.token.DSSPrivateKeyEntry;
import eu.europa.ec.markt.dss.validation102853.CommonCertificateVerifier;

/**
 * This class checks if the getDataToSign result is equals when passing the same
 * parameters
 *
 */
public class DigestStabilityTest {

	@Test
	public void testTwiceGetDataToSignReturnsSameDigest() throws Exception {
		DSSDocument toBeSigned = new FileDocument(new File("src/test/resources/sample.pdf"));

		CertificateService certificateService = new CertificateService();
		DSSPrivateKeyEntry privateKeyEntry = certificateService.generateCertificateChain(SignatureAlgorithm.RSA_SHA256);

		Date signingDate = new Date();

		byte[] dataToSign1 = getDataToSign(toBeSigned, privateKeyEntry, signingDate);
		byte[] dataToSign2 = getDataToSign(toBeSigned, privateKeyEntry, signingDate);

		final MessageDigest messageDigest = MessageDigest.getInstance(DigestAlgorithm.SHA256.getOid().getId());
		byte[] digest1 = messageDigest.digest(dataToSign1);
		byte[] digest2 = messageDigest.digest(dataToSign2);

		// Doesn't work, the static field SignatureParameters.signatureCounter
		// is incremented

		assertEquals(Base64.encodeBase64String(digest1), Base64.encodeBase64String(digest2));
	}

	private byte[] getDataToSign(DSSDocument toBeSigned, DSSPrivateKeyEntry privateKeyEntry, Date signingDate) {

		DocumentSignatureService service = new PAdESService(new CommonCertificateVerifier());

		SignatureParameters signatureParameters = new SignatureParameters();
		signatureParameters.bLevel().setSigningDate(signingDate);
		signatureParameters.setSigningCertificate(privateKeyEntry.getCertificate());
		signatureParameters.setCertificateChain(privateKeyEntry.getCertificateChain());
		signatureParameters.setSignaturePackaging(SignaturePackaging.ENVELOPING);
		signatureParameters.setSignatureLevel(SignatureLevel.PAdES_BASELINE_B);

		return service.getDataToSign(toBeSigned, signatureParameters);
	}

}
