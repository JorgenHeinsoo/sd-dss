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
package eu.europa.ec.markt.dss.validation102853.xades;

import eu.europa.ec.markt.dss.DSSUtils;
import eu.europa.ec.markt.dss.DSSXMLUtils;
import eu.europa.ec.markt.dss.validation102853.ocsp.OfflineOCSPSource;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Retrieves OCSP values from an XAdES (>XL) signature.
 *
 *
 */

public class XAdESOCSPSource extends OfflineOCSPSource implements Serializable {

    private final Element signatureElement;

    private final XPathQueryHolder xPathQueryHolder;

    /**
     * The default constructor for XAdESOCSPSource.
     *
     * @param signatureElement {@code Element} that contains an XML signature
     * @param xPathQueryHolder adapted {@code XPathQueryHolder}
     */
    public XAdESOCSPSource(final Element signatureElement, final XPathQueryHolder xPathQueryHolder) {

        this.signatureElement = signatureElement;
        this.xPathQueryHolder = xPathQueryHolder;
    }

    @Override
    public List<BasicOCSPResp> getContainedOCSPResponses() {

        final List<BasicOCSPResp> list = new ArrayList<BasicOCSPResp>();
        addOCSP(list, xPathQueryHolder.XPATH_ENCAPSULATED_OCSP_VALUE);
        addOCSP(list, xPathQueryHolder.XPATH_TSVD_ENCAPSULATED_OCSP_VALUE);
        return list;
    }

    private void addOCSP(final List<BasicOCSPResp> list, final String xPathQuery) {

        final NodeList nodeList = DSSXMLUtils.getNodeList(signatureElement, xPathQuery);
        for (int ii = 0; ii < nodeList.getLength(); ii++) {

            final Element certEl = (Element) nodeList.item(ii);
            final BasicOCSPResp basicOCSPResp = DSSUtils.loadOCSPBase64Encoded(certEl.getTextContent());
            list.add(basicOCSPResp);
        }
    }
}
