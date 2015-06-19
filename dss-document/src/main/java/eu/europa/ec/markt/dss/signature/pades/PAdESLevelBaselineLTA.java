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

import java.util.List;

import eu.europa.ec.markt.dss.exception.DSSException;
import eu.europa.ec.markt.dss.parameter.SignatureParameters;
import eu.europa.ec.markt.dss.signature.DSSDocument;
import eu.europa.ec.markt.dss.signature.SignatureExtension;
import eu.europa.ec.markt.dss.signature.SignatureLevel;
import eu.europa.ec.markt.dss.signature.validation.AdvancedSignature;
import eu.europa.ec.markt.dss.validation102853.CertificateVerifier;
import eu.europa.ec.markt.dss.validation102853.pades.PDFDocumentValidator;
import eu.europa.ec.markt.dss.validation102853.tsp.TSPSource;

/**
 * TODO
 *
 *
 *
 *
 *
 *
 */
class PAdESLevelBaselineLTA implements SignatureExtension {

    private final PAdESLevelBaselineLT padesLevelBaselineLT;
    private final PAdESLevelBaselineT padesProfileT;
    private final CertificateVerifier certificateVerifier;

    public PAdESLevelBaselineLTA(TSPSource tspSource, CertificateVerifier certificateVerifier) {

        padesLevelBaselineLT = new PAdESLevelBaselineLT(tspSource, certificateVerifier);
        padesProfileT = new PAdESLevelBaselineT(tspSource, certificateVerifier);
        this.certificateVerifier = certificateVerifier;
    }

    @Override
    public DSSDocument extendSignatures(DSSDocument document, SignatureParameters params) throws DSSException {

        final PDFDocumentValidator pdfDocumentValidator = new PDFDocumentValidator(document);
        pdfDocumentValidator.setCertificateVerifier(certificateVerifier);
        final List<AdvancedSignature> signatures = pdfDocumentValidator.getSignatures();
        for (final AdvancedSignature signature : signatures) {

            if (!signature.isDataForSignatureLevelPresent(SignatureLevel.PAdES_BASELINE_LT)) {

                document = padesLevelBaselineLT.extendSignatures(document, params);
                // PAdES LT already add a timestamp on top of the LT data. No need to timestamp again.
                return document;
            }
        }
        return padesProfileT.extendSignatures(document, params);
    }
}
