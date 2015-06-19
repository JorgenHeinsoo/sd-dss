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
package eu.europa.ec.markt.dss.validation102853;

import java.io.IOException;
import java.io.StringWriter;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.ocsp.ResponderID;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RespID;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.ec.markt.dss.DSSASN1Utils;
import eu.europa.ec.markt.dss.DSSRevocationUtils;
import eu.europa.ec.markt.dss.DSSUtils;
import eu.europa.ec.markt.dss.SignatureAlgorithm;
import eu.europa.ec.markt.dss.exception.DSSException;
import eu.europa.ec.markt.dss.exception.DSSNullException;
import eu.europa.ec.markt.dss.validation102853.certificate.CertificateSourceType;

/**
 * OCSP Signed Token which encapsulate BasicOCSPResp (BC).
 *
 *
 */

public class OCSPToken extends RevocationToken {

	private static final Logger LOG = LoggerFactory.getLogger(OCSPToken.class);

	/**
	 * The encapsulated basic OCSP response.
	 */
	private transient final BasicOCSPResp basicOCSPResp;

	private transient final SingleResp singleResp;

	/**
	 * In case of online source this is the source URI.
	 */
	private String sourceURI;

	/**
	 * The default constructor for OCSPToken.
	 *
	 * @param basicOCSPResp   The basic OCSP response.
	 * @param singleResp
	 * @param certificatePool The certificate pool used to validate/hold the certificate used to sign this OCSP response.
	 */
	public OCSPToken(final BasicOCSPResp basicOCSPResp, final SingleResp singleResp, final CertificatePool certificatePool) {

		if (basicOCSPResp == null) {
			throw new DSSNullException(BasicOCSPResp.class);
		}
		if (singleResp == null) {
			throw new DSSNullException(SingleResp.class);
		}
		if (certificatePool == null) {
			throw new DSSNullException(CertificatePool.class);
		}
		this.basicOCSPResp = basicOCSPResp;
		this.singleResp = singleResp;
		this.issuingTime = basicOCSPResp.getProducedAt();
		setStatus(singleResp.getCertStatus());
		final ASN1ObjectIdentifier signatureAlgOID = basicOCSPResp.getSignatureAlgOID();
		final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.forOID(signatureAlgOID.getId());
		this.algorithmUsedToSignToken = signatureAlgorithm;
		this.extraInfo = new TokenValidationExtraInfo();

		final boolean found = extractSigningCertificateFromResponse(certificatePool);
		if (!found) {
			extractSigningCertificateFormResponderId(certificatePool);
		}
		if (LOG.isTraceEnabled()) {
			LOG.trace("OCSP token, produced at '" + DSSUtils.formatInternal(issuingTime) + "' created.");
		}
	}

	private void extractSigningCertificateFormResponderId(final CertificatePool certificatePool) {

		final RespID responderId = basicOCSPResp.getResponderId();
		final ResponderID responderIdAsASN1Object = responderId.toASN1Object();
		final DERTaggedObject derTaggedObject = (DERTaggedObject) responderIdAsASN1Object.toASN1Primitive();
		if (2 == derTaggedObject.getTagNo()) {

			throw new DSSException("Certificate's key hash management not implemented yet!");
		}
		final ASN1Primitive derObject = derTaggedObject.getObject();
		final byte[] derEncoded = DSSASN1Utils.getDEREncoded(derObject);
		final X500Principal x500Principal_ = new X500Principal(derEncoded);
		final X500Principal x500Principal = DSSUtils.getX500Principal(x500Principal_);
		final List<CertificateToken> certificateTokens = certificatePool.get(x500Principal);
		for (final CertificateToken issuerCertificateToken : certificateTokens) {
			if (isSignedBy(issuerCertificateToken)) {
				break;
			}
		}
	}

	private boolean extractSigningCertificateFromResponse(final CertificatePool certificatePool) {

		for (final X509CertificateHolder x509CertificateHolder : basicOCSPResp.getCerts()) {

			final byte[] encoded = DSSUtils.getEncoded(x509CertificateHolder);
			final CertificateToken x509Certificate = DSSUtils.loadCertificate(encoded);
			final CertificateToken certToken = certificatePool.getInstance(x509Certificate, CertificateSourceType.OCSP_RESPONSE);
			if (isSignedBy(certToken)) {

				return true;
			}
		}
		return false;
	}

	private void setStatus(final CertificateStatus certStatus) {

		if (certStatus == null) {
			status = true;
			return;
		}
		if (LOG.isInfoEnabled()) {
			LOG.info("OCSP certificate status: " + certStatus.getClass().getName());
		}
		if (certStatus instanceof RevokedStatus) {

			if (LOG.isInfoEnabled()) {
				LOG.info("OCSP status revoked");
			}
			final RevokedStatus revokedStatus = (RevokedStatus) certStatus;
			status = false;
			revocationDate = revokedStatus.getRevocationTime();
			int reasonId = 0;
			try {
				reasonId = revokedStatus.getRevocationReason();
			} catch (IllegalStateException e) {
				LOG.warn(e.getMessage());
			}
			final CRLReason crlReason = CRLReason.lookup(reasonId);
			reason = crlReason.toString();
		} else if (certStatus instanceof UnknownStatus) {

			if (LOG.isInfoEnabled()) {
				LOG.info("OCSP status unknown");
			}
			reason = "OCSP status: unknown";
		}
	}

	/**
	 * @return the ocspResp
	 */

	public BasicOCSPResp getBasicOCSPResp() {

		return basicOCSPResp;
	}

	@Override
	public boolean isSignedBy(final CertificateToken issuerToken) {

		if (this.issuerToken != null) {

			return this.issuerToken.equals(issuerToken);
		}
		try {

			signatureInvalidityReason = "";
			JcaContentVerifierProviderBuilder jcaContentVerifierProviderBuilder = new JcaContentVerifierProviderBuilder();
			jcaContentVerifierProviderBuilder.setProvider("BC");
			final PublicKey publicKey = issuerToken.getCertificate().getPublicKey();
			ContentVerifierProvider contentVerifierProvider = jcaContentVerifierProviderBuilder.build(publicKey);
			signatureValid = basicOCSPResp.isSignatureValid(contentVerifierProvider);
			if (signatureValid) {

				this.issuerToken = issuerToken;
			}
			issuerX500Principal = issuerToken.getSubjectX500Principal();
		} catch (OCSPException e) {

			signatureInvalidityReason = e.getClass().getSimpleName() + " - " + e.getMessage();
			signatureValid = false;
		} catch (OperatorCreationException e) {
			signatureInvalidityReason = e.getClass().getSimpleName() + " - " + e.getMessage();
			signatureValid = false;
		}
		return signatureValid;
	}

	public String getSourceURL() {

		return sourceURI;
	}

	public void setSourceURI(final String sourceURI) {

		this.sourceURI = sourceURI;
	}

	/**
	 * Indicates if the token signature is intact.
	 *
	 * @return {@code true} or {@code false}
	 */
	@Override
	public boolean isValid() {

		return signatureValid;
	}

	/**
	 * This method returns the DSS abbreviation of the certificate. It is used for debugging purpose.
	 *
	 * @return
	 */
	public String getAbbreviation() {

		return "OCSPToken[" + DSSUtils.formatInternal(basicOCSPResp.getProducedAt()) + ", signedBy=" + (issuerToken == null ? "?" : issuerToken.getDSSIdAsString()) +
			  "]";
	}

	@Override
	public String toString(String indentStr) {

		final StringWriter out = new StringWriter();
		out.append(indentStr).append("OCSPToken[");
		out.append("ProductionTime: ").append(DSSUtils.formatInternal(issuingTime)).append("; ");
		out.append("ThisUpdate: ").append(DSSUtils.formatInternal(singleResp.getThisUpdate())).append("; ");
		out.append("NextUpdate: ").append(DSSUtils.formatInternal(singleResp.getNextUpdate())).append('\n');
		out.append("SignedBy: ").append(issuerToken != null ? issuerToken.getDSSIdAsString() : null).append('\n');
		indentStr += "\t";
		out.append(indentStr).append("Signature algorithm: ").append(algorithmUsedToSignToken == null ? "?" : algorithmUsedToSignToken.getJCEId()).append('\n');
		out.append(issuerToken != null ? issuerToken.toString(indentStr) : null).append('\n');
		final List<String> validationExtraInfo = extraInfo.getValidationInfo();
		if (validationExtraInfo.size() > 0) {

			for (final String info : validationExtraInfo) {

				out.append('\n').append(indentStr).append("\t- ").append(info);
			}
			out.append('\n');
		}
		indentStr = indentStr.substring(1);
		out.append(indentStr).append("]");
		return out.toString();
	}

	@Override
	public byte[] getEncoded() {

		final OCSPResp ocspResp = DSSRevocationUtils.fromBasicToResp(basicOCSPResp);
		try {

			final byte[] bytes = ocspResp.getEncoded();
			return bytes;
		} catch (IOException e) {
			throw new DSSException("CRL encoding error: " + e.getMessage(), e);
		}
	}
}
