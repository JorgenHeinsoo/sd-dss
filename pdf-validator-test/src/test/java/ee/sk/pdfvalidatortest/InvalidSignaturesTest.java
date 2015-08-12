package ee.sk.pdfvalidatortest;

import org.junit.Test;
import org.w3c.dom.Document;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class InvalidSignaturesTest extends PdfValidatorSoapTests {

    @Test
    public void missingSignedAttributeForSigningCertificate() {
        String httpBody = post(validationRequestFor(readFile("missing_signing_certificate_attribute.pdf"))).
                andReturn().body().asString();

        assertEquals(
                "The signed attribute: 'signing-certificate' is absent!",
                findErrorById("BBB_ICS_ISASCP_ANS", detailedReport(httpBody)));
    }


    @Test
    public void adesLtaBaselineProfileShouldPass() {
        String httpBody = post(validationRequestFor(readFile("Signature-P-EE_AS-7.pdf"))).
                andReturn().body().asString();

        assertEquals(1, validSignatures(simpleReport(httpBody)));
    }

    @Test
    public void adesTBaselineProfileShouldFail() {
        String httpBody = post(validationRequestFor(readFile("Signature-P-EE_AS-5.pdf"))).
                andReturn().body().asString();

        assertEquals(0, validSignatures(simpleReport(httpBody)));
    }

    @Test
    public void noOcspRequestsAreMadeForBaselineB() {
        String httpBody = post(validationRequestFor(readFile("Signature-P-AT-1.pdf"))).
                andReturn().body().asString();

        assertEquals(0, validSignatures(simpleReport(httpBody)));
    }

    @Test
    public void adesBBaselineRevokedShouldFail() {
        String httpBody = post(validationRequestFor(readFile("Signature-P-EE_AS-1.pdf"))).
                andReturn().body().asString();
        System.out.print(httpBody);
        assertEquals(0, validSignatures(simpleReport(httpBody)));

    }

    @Test
    public void adesLtBaselineShouldPass() {
        String httpBody = post(validationRequestFor(readFile("hellopades-pades-lt-sha256-sign.pdf"))).
                andReturn().body().asString();

        assertEquals(1, validSignatures(simpleReport(httpBody)));

    }

    @Test
    public void adesLtBaselineNonRepudiationIsMandatoryShouldFail() {
        String httpBody = post(validationRequestFor(readFile("hellopades-pades-lt-sha256-auth.pdf"))).
                andReturn().body().asString();
        assertEquals(0, validSignatures(simpleReport(httpBody)));
    }

    @Test
    public void adesLtaBaselineNoOcspCrlInSignatureShouldFail() {
        String httpBody = post(validationRequestFor(readFile("hellopades-lta-no-ocsp.pdf"))).
                andReturn().body().asString();
        assertEquals(0, validSignatures(simpleReport(httpBody)));
    }

    @Test
    public void adesLtBaselineOcsp15MinDelayShouldPass() {
        String httpBody = post(validationRequestFor(readFile("hellopades-lt-sha256-ocsp-15min.pdf"))).
                andReturn().body().asString();
        System.out.println(httpBody);
        assertEquals(1, validSignatures(simpleReport(httpBody)));
    }



    @Test
    public void adesPdfSignedWithSha512CertifikateShouldPass() {
        String httpBody = post(validationRequestFor(readFile("hellopades-lt-sha512.pdf"))).
                andReturn().body().asString();
        assertEquals(1, validSignatures(simpleReport(httpBody)));
    }

    @Test
    public void adesPdfSignedWithSha1CertifikateShouldPass() {
        String httpBody = post(validationRequestFor(readFile("hellopades-lt-sha1.pdf"))).
                andReturn().body().asString();
        assertEquals(1, validSignatures(simpleReport(httpBody)));
    }

    @Test
    public void adesPdfSignedWithRevokedCertificateShouldFail() {
        String httpBody = post(validationRequestFor(readFile("hellopades-lt-sha256-revoked.pdf"))).
                andReturn().body().asString();
        assertEquals(0, validSignatures(simpleReport(httpBody)));
    }





    private byte[] readFile(String fileName) {
        return readFileFromTestResources("invalid_signature_documents/", fileName);
    }

    private String findErrorById(String errorId, Document detailedReport) {
        return XmlUtil.findElementByXPath(
                detailedReport,
                "//d:Error[@NameId='" + errorId + "']",
                Collections.singletonMap("d", "http://dss.markt.ec.europa.eu/validation/diagnostic")).getTextContent();
    }

    private int validSignatures(Document simpleReport) {
        String stringResult = XmlUtil.findElementByXPath(
                simpleReport,
                "//d:SimpleReport/d:ValidSignaturesCount",
                Collections.singletonMap("d", "http://dss.markt.ec.europa.eu/validation/diagnostic")).getTextContent();

        return Integer.parseInt(stringResult);
    }

    private Document detailedReport(String httpBody) {
        Document document = XmlUtil.parseXml(httpBody);
        String detailedReportString = XmlUtil.findElementByXPath(document, "//xmlDetailedReport").getTextContent();
        return XmlUtil.parseXml(detailedReportString);
    }

    private Document simpleReport(String httpBody) {
        Document document = XmlUtil.parseXml(httpBody);
        String detailedReportString = XmlUtil.findElementByXPath(document, "//xmlSimpleReport").getTextContent();
        return XmlUtil.parseXml(detailedReportString);
    }


}