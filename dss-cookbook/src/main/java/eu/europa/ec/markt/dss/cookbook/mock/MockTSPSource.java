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
package eu.europa.ec.markt.dss.cookbook.mock;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.Set;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.Time;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.SignerInfoGeneratorBuilder;
import org.bouncycastle.cms.SimpleAttributeTableGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TSPException;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.tsp.TimeStampTokenGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.ec.markt.dss.DSSUtils;
import eu.europa.ec.markt.dss.DigestAlgorithm;
import eu.europa.ec.markt.dss.TokenIdentifier;
import eu.europa.ec.markt.dss.exception.DSSException;
import eu.europa.ec.markt.dss.validation102853.tsp.TSPSource;

public class MockTSPSource implements TSPSource {

	private static final Logger LOG = LoggerFactory.getLogger(MockTSPSource.class);
	private Date lastTimestampDate = null;
	private ASN1ObjectIdentifier policyOid;
	private final PrivateKey key;
	private final X509Certificate cert;
	private final X509Certificate trustedAnchor;
	private final Date timestampDate;

	/**
	 * The default constructor for MockTSPSource.
	 */
	public MockTSPSource() throws DSSException {

		this(new Date());
	}

	public MockTSPSource(final Date timestampDate) throws DSSException {

		this(MockTSPSource.class.getResourceAsStream("/tsa.p12"), "password", timestampDate);
		this.setPolicyOid("1.234.567.890");
	}

	/**
	 * The default constructor for MockTSPSource.
	 *
	 * @param signerPkcs12
	 * @param password
	 * @throws DSSException
	 */
	public MockTSPSource(final InputStream signerPkcs12, final String password, final Date timestampDate) throws DSSException {
		this.timestampDate = timestampDate;
		Security.addProvider(new BouncyCastleProvider());

		try {
			KeyStore ks = KeyStore.getInstance("PKCS12");
			ks.load(signerPkcs12, password.toCharArray());
			String alias = ks.aliases().nextElement();

			key = (PrivateKey) ks.getKey(alias, password.toCharArray());
			cert = (X509Certificate) ks.getCertificate(alias);

			trustedAnchor = (X509Certificate) ks.getCertificateChain(alias)[1];

			LOG.debug("TSP mockup with certificate {}", TokenIdentifier.getId(cert));
		} catch (KeyStoreException e) {
			throw new DSSException(e);
		} catch (IOException e) {
			throw new DSSException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new DSSException(e);
		} catch (CertificateException e) {
			throw new DSSException(e);
		} catch (UnrecoverableKeyException e) {
			throw new DSSException(e);
		}
	}

	public X509Certificate getTrustedAnchor() {

		return trustedAnchor;
	}

	@Override
	public TimeStampToken getTimeStampResponse(final DigestAlgorithm digestAlgorithm, final byte[] digest) throws DSSException {

		final String signatureAlgorithm = getSignatureAlgorithm(digestAlgorithm, digest);

		final TimeStampRequestGenerator tsqGenerator = new TimeStampRequestGenerator();
		tsqGenerator.setCertReq(true);
		/**
		 * The code below guarantee that the dates of the two successive timestamps are different.
		 */
		if (lastTimestampDate == null) {
			lastTimestampDate = timestampDate;
		} else {

			final long time = lastTimestampDate.getTime() + 1000;
			lastTimestampDate = new Date(time);
		}
		final Date timestampDate_ = lastTimestampDate;
		LOG.debug("-->#######:O:" + timestampDate.toString());
		LOG.debug("-->#######:N:" + timestampDate_.toString());
		final BigInteger nonce = BigInteger.valueOf(timestampDate.getTime());
		final TimeStampRequest tsRequest = tsqGenerator.generate(digestAlgorithm.getOid(), digest, nonce);
		if (policyOid != null) {

			tsqGenerator.setReqPolicy(policyOid);
		}

		try {
			final ContentSigner sigGen = new JcaContentSignerBuilder(signatureAlgorithm).build(key);
			final JcaX509CertificateHolder certHolder = new JcaX509CertificateHolder(cert);

			// that to make sure we generate the same timestamp data for the same timestamp date
			AttributeTable signedAttributes = new AttributeTable(new Hashtable());
			signedAttributes = signedAttributes.add(PKCSObjectIdentifiers.pkcs_9_at_signingTime, new Time(timestampDate_));
			final DefaultSignedAttributeTableGenerator signedAttributeGenerator = new DefaultSignedAttributeTableGenerator(signedAttributes);
			AttributeTable unsignedAttributes = new AttributeTable(new Hashtable());
			final SimpleAttributeTableGenerator unsignedAttributeGenerator = new SimpleAttributeTableGenerator(unsignedAttributes);

			final DigestCalculatorProvider digestCalculatorProvider = new BcDigestCalculatorProvider();
			SignerInfoGeneratorBuilder sigInfoGeneratorBuilder = new SignerInfoGeneratorBuilder(digestCalculatorProvider);
			sigInfoGeneratorBuilder.setSignedAttributeGenerator(signedAttributeGenerator);
			sigInfoGeneratorBuilder.setUnsignedAttributeGenerator(unsignedAttributeGenerator);
			final SignerInfoGenerator sig = sigInfoGeneratorBuilder.build(sigGen, certHolder);

			final DigestCalculator sha1DigestCalculator = DSSUtils.getSHA1DigestCalculator();

			final TimeStampTokenGenerator tokenGenerator = new TimeStampTokenGenerator(sig, sha1DigestCalculator, policyOid);
			final Set<X509Certificate> singleton = Collections.singleton(cert);
			tokenGenerator.addCertificates(new JcaCertStore(singleton));
			final TimeStampResponseGenerator generator = new TimeStampResponseGenerator(tokenGenerator, TSPAlgorithms.ALLOWED);

			TimeStampResponse tsResponse = generator.generate(tsRequest, BigInteger.ONE, timestampDate_);
			final TimeStampToken timeStampToken = tsResponse.getTimeStampToken();
			return timeStampToken;
		} catch (OperatorCreationException e) {
			throw new DSSException(e);
		} catch (CertificateEncodingException e) {
			throw new DSSException(e);
		} catch (TSPException e) {
			throw new DSSException(e);
		}
	}

	@Override
	public void setPolicyOid(final String policyOid) {

		this.policyOid = new ASN1ObjectIdentifier(policyOid);
	}

	@Override
	public String getUniqueId(byte[] digestValue) {

		final byte[] digest = DSSUtils.digest(DigestAlgorithm.MD5, digestValue, DSSUtils.toByteArray(timestampDate.getTime()));
		return DSSUtils.toHex(digest);
	}

	private String getSignatureAlgorithm(DigestAlgorithm algorithm, byte[] digest) {

		String signatureAlgorithm;
		if (DigestAlgorithm.SHA1.equals(algorithm)) {
			signatureAlgorithm = "SHA1withRSA";
			if (digest.length != 20) {
				throw new IllegalArgumentException("Not valid size for a SHA1 digest : " + digest.length + " bytes");
			}
		} else if (DigestAlgorithm.SHA256.equals(algorithm)) {
			signatureAlgorithm = "SHA256withRSA";
			if (digest.length != 32) {
				throw new IllegalArgumentException("Not valid size for a SHA256 digest : " + digest.length + " bytes");
			}
		} else {

			throw new UnsupportedOperationException("No support for " + algorithm);
		}
		return signatureAlgorithm;
	}

	public X509Certificate getSigningCertificate() {

		return cert;
	}
}
