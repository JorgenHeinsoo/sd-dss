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
package eu.europa.ec.markt.dss.ws.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.jws.WebService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.ec.markt.dss.exception.DSSException;
import eu.europa.ec.markt.dss.exception.DSSNullException;
import eu.europa.ec.markt.dss.signature.DSSDocument;
import eu.europa.ec.markt.dss.validation102853.CertificateVerifier;
import eu.europa.ec.markt.dss.validation102853.SignedDocumentValidator;
import eu.europa.ec.markt.dss.validation102853.report.DetailedReport;
import eu.europa.ec.markt.dss.validation102853.report.DiagnosticData;
import eu.europa.ec.markt.dss.validation102853.report.Reports;
import eu.europa.ec.markt.dss.validation102853.report.SimpleReport;
import eu.europa.ec.markt.dss.ws.DSSWSUtils;
import eu.europa.ec.markt.dss.ws.ValidationService;
import eu.europa.ec.markt.dss.ws.WSDocument;
import eu.europa.ec.markt.dss.ws.report.WSValidationReport;

/**
 * Implementation of the Interface for the Contract of the Validation Web Service.
 *
 *
 */
@WebService(endpointInterface = "eu.europa.ec.markt.dss.ws.ValidationService", serviceName = "ValidationService")
public class ValidationServiceImpl implements ValidationService {

	private static final Logger logger = LoggerFactory.getLogger(SignatureServiceImpl.class);

	private CertificateVerifier certificateVerifier;

	/**
	 * @param certificateVerifier the certificateVerifier to set
	 */
	public void setCertificateVerifier(CertificateVerifier certificateVerifier) {
		this.certificateVerifier = certificateVerifier;
	}

	@Override
	public WSValidationReport validateDocument(WSDocument wsDocument, WSDocument wsDetachedContents, WSDocument policy, boolean diagnosticDataToBeReturned) throws DSSException {

		String exceptionMessage;
		try {
			if (logger.isInfoEnabled()) {

				logger.info("WsValidateDocument: begin");
			}
			if (wsDocument == null) {

				throw new DSSNullException(WSDocument.class);
			}
			final DSSDocument dssDocument = DSSWSUtils.createDssDocument(wsDocument);
			final SignedDocumentValidator validator = SignedDocumentValidator.fromDocument(dssDocument);
			validator.setCertificateVerifier(certificateVerifier);
			if (wsDetachedContents != null) {

				List<DSSDocument> detachedContentsList = new ArrayList<DSSDocument>();
				DSSDocument dssDetachedContents = DSSWSUtils.createDssDocument(wsDetachedContents);
				detachedContentsList.add(dssDetachedContents);
				validator.setDetachedContents(detachedContentsList);
			}

			final InputStream inputStream = policy == null ? null : policy.openStream();
			final Reports reports = validator.validateDocument(inputStream);

			final SimpleReport simpleReport = reports.getSimpleReport();
			final String simpleReportXml = simpleReport.toString();

			final DetailedReport detailedReport = reports.getDetailedReport();
			final String detailedReportXml = detailedReport.toString();

			final WSValidationReport wsValidationReport = new WSValidationReport();
			wsValidationReport.setXmlSimpleReport(simpleReportXml);
			wsValidationReport.setXmlDetailedReport(detailedReportXml);
			if (diagnosticDataToBeReturned) {

				final DiagnosticData diagnosticData = reports.getDiagnosticData();
				final String diagnosticDataXml = diagnosticData.toString();
				wsValidationReport.setXmlDiagnosticData(diagnosticDataXml);
			}
			if (logger.isInfoEnabled()) {

				logger.info("WsValidateDocument: end");
			}
			return wsValidationReport;
		} catch (Throwable e) {
			logger.error(e.getMessage(), e);
			exceptionMessage = e.getMessage();
		}
		logger.info("WsValidateDocument: end with exception");
		throw new DSSException(exceptionMessage);
	}
}