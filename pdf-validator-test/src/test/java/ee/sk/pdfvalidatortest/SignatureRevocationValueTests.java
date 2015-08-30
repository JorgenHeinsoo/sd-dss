package ee.sk.pdfvalidatortest;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SignatureRevocationValueTests extends PdfValidatorSoapTests {

    @Test
    public void DocumentWithOcspOver15MinDelayShouldPassButWarn() {
        String httpBody = post(validationRequestFor(readFile("hellopades-lt-sha256-ocsp-15min1s.pdf"))).
                andReturn().body().asString();

        assertEquals(
                "The validation failed, because OCSP is too long after the best-signature-time!",
                findWarningById("ADEST_IOTNLABST_ANS", detailedReport(httpBody)));
        assertEquals(1, validSignatures(simpleReport(httpBody)));
    }

    @Test
    public void DocumentWithOcspOver24hDelayShouldFail() {
        String httpBody = post(validationRequestFor(readFile("hellopades-lt-sha256-ocsp-28h.pdf"))).
                andReturn().body().asString();

        assertEquals(
                "The validation failed, because OCSP is too long after the best-signature-time!",
                findErrorById("ADEST_IOTNLABST_ANS", detailedReport(httpBody)));
        assertEquals(0, validSignatures(simpleReport(httpBody)));
    }

    @Test
    public void DocumentWithNoOcspNorCrlInSignatureShouldFail() {
        String httpBody = post(validationRequestFor(readFile("hellopades-lta-no-ocsp.pdf"))).
                andReturn().body().asString();

        assertEquals(0, validSignatures(simpleReport(httpBody)));
    }

    @Ignore // needs test file
    @Test
    public void DocumentSignedWithOcspTimeValueBeforeBestSignatureTimeShouldFail() {
        String httpBody = post(validationRequestFor(readFile("some_pdf_file"))).
                andReturn().body().asString();

        assertEquals(
                "The validation failed, because OCSP is before the best-signature-time!",
                findErrorById("ADEST_IOABST_ANS", detailedReport(httpBody)));
        assertEquals(0, validSignatures(simpleReport(httpBody)));
    }

    protected byte[] readFile(String fileName) {
        return readFileFromTestResources("signature_revocation_value_test_files/", fileName);
    }

}
