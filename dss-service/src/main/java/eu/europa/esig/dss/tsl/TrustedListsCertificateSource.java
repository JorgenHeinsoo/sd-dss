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
package eu.europa.esig.dss.tsl;

import eu.europa.esig.dss.*;
import eu.europa.esig.dss.client.http.DataLoader;
import eu.europa.esig.dss.client.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.validation.AdvancedSignature;
import eu.europa.esig.dss.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.validation.ValidationResourceManager;
import eu.europa.esig.dss.validation.report.Reports;
import eu.europa.esig.dss.validation.report.SimpleReport;
import eu.europa.esig.dss.x509.CertificateSourceType;
import eu.europa.esig.dss.x509.CertificateToken;
import eu.europa.esig.dss.x509.CommonTrustedCertificateSource;
import eu.europa.esig.dss.x509.KeyStoreCertificateSource;
import eu.europa.esig.dss.xades.validation.XMLDocumentValidator;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.Security;
import java.util.*;

/**
 * This class allows to extract all the trust anchors defined by the trusted lists. The LOTL is used as the entry point of the process.
 */

public class TrustedListsCertificateSource extends CommonTrustedCertificateSource {

	private static final Logger logger = LoggerFactory.getLogger(TrustedListsCertificateSource.class);

	public static final String TSL_HASH_PROPERTIES = "tsl_hash.properties";
	public static final String TSL_NEXT_UPDATE_PROPERTIES = "tsl_next_update.properties";

	private File tslPropertyCacheFolder = new File(System.getProperty("java.io.tmpdir"));
	private Properties tslHashes = null;
	private Properties tslNextUpdates = null;

	protected TSLRefreshPolicy tslRefreshPolicy = TSLRefreshPolicy.ALWAYS;

	private CommonsDataLoader commonsDataLoader = new CommonsDataLoader();

	protected String lotlUrl;

	protected transient DataLoader dataLoader;

	private Map<String, DiagnosticInfo> diagnosticInfo = new LinkedHashMap<>();

	private KeyStoreCertificateSource keyStoreCertificateSource;

	/**
	 * Defines if the TL signature must be checked. The default value is true.
	 */
	protected boolean checkSignature = true;

	static {

		Security.addProvider(new BouncyCastleProvider());
	}

	/**
	 * The default constructor.
	 */
	public TrustedListsCertificateSource() {
		super();
	}

	/**
	 * The copy constructor.
	 *
	 * @param trustedListsCertificateSource
	 */
	public TrustedListsCertificateSource(final TrustedListsCertificateSource trustedListsCertificateSource) {

		this.setDataLoader(trustedListsCertificateSource.dataLoader);
		this.setCheckSignature(trustedListsCertificateSource.checkSignature);
		this.setKeyStoreCertificateSource(trustedListsCertificateSource.keyStoreCertificateSource);
		this.setLotlUrl(trustedListsCertificateSource.lotlUrl);
		this.setTslPropertyCacheFolder(trustedListsCertificateSource.tslPropertyCacheFolder);
		this.setTslRefreshPolicy(trustedListsCertificateSource.tslRefreshPolicy);
	}

	@Override
	protected CertificateSourceType getCertificateSourceType() {

		return CertificateSourceType.TRUSTED_LIST;
	}

	/**
	 * This method is not applicable for this kind of certificate source. You should use {@link #addCertificate(java.security.cert.X509Certificate, eu.europa.esig.dss.tsl.ServiceInfo)}
	 *
	 * @param x509Certificate
	 *            the certificate you have to trust
	 * @return the corresponding certificate token
	 */
	@Override
	public CertificateToken addCertificate(final CertificateToken x509Certificate) {

		throw new DSSNotApplicableMethodException(getClass());
	}

	/**
	 * Adds a service entry (current or history) to the list of certificate tokens.
	 *
	 * @param x509Certificate
	 *            the certificate which identifies the trusted service
	 * @param trustedService
	 *            Object defining the trusted service
	 * @param tsProvider
	 *            Object defining the trusted service provider, must be the parent of the trusted service
	 * @param tlWellSigned
	 *            Indicates if the signature of trusted list is valid
	 */
	private synchronized void addCertificate(final CertificateToken x509Certificate, final AbstractTrustService trustedService, final TrustServiceProvider tsProvider,
			final boolean tlWellSigned) {

		try {
			final ServiceInfo serviceInfo = getServiceInfo(trustedService, tsProvider, tlWellSigned);
			addCertificate(x509Certificate, serviceInfo);
		} catch (DSSNotETSICompliantException ex) {

			logger.error("The entry for " + trustedService.getServiceName() + " doesn't respect ETSI specification " + ex.getLocalizedMessage());
		}
	}

	/**
	 * This method return the service info object enclosing the certificate.
	 *
	 * @param trustedService
	 *            Object defining the trusted service
	 * @param tsProvider
	 *            Object defining the trusted service provider, must be the parent of the trusted service
	 * @param tlWellSigned
	 *            Indicates if the signature of trusted list is valid
	 * @return
	 */
	private ServiceInfo getServiceInfo(final AbstractTrustService trustedService, final TrustServiceProvider tsProvider, final boolean tlWellSigned) {

		// System.out.println("--- > ServiceName: " + trustedService.getServiceName());
		final ServiceInfo serviceInfo = trustedService.createServiceInfo();

		serviceInfo.setServiceName(trustedService.getServiceName());
		serviceInfo.setStatus(trustedService.getStatus());
		serviceInfo.setStatusStartDate(trustedService.getStatusStartDate());
		serviceInfo.setStatusEndDate(trustedService.getStatusEndDate());
		serviceInfo.setType(trustedService.getType());

		serviceInfo.setTspElectronicAddress(tsProvider.getElectronicAddress());
		serviceInfo.setTspName(tsProvider.getName());
		serviceInfo.setTspPostalAddress(tsProvider.getPostalAddress());
		serviceInfo.setTspTradeName(tsProvider.getTradeName());

		serviceInfo.setTlWellSigned(tlWellSigned);

		return serviceInfo;
	}

	/**
	 * This method returns the diagnostic data concerning the certificates retrieval process from the trusted lists. It can be used for
	 * debugging purposes.
	 *
	 * @return the diagnosticInfo
	 */
	public Map<String, String> getDiagnosticInfo() {
		Map<String, String> result = new LinkedHashMap<>();
		for (Map.Entry<String, DiagnosticInfo> entry : diagnosticInfo.entrySet()) {
			result.put(entry.getKey(), entry.getValue().getLoadingDiagnosticInfo());
		}
		return Collections.unmodifiableMap(result); // Returning an unmodifiable map to remain strictly compatible with upstream API
	}

	public Map<String, DiagnosticInfo> getDiagnosticInfoExtended() {
		return diagnosticInfo;
	}

	public void setDiagnosticInfo(Map<String, DiagnosticInfo> diagnosticInfo) {
		this.diagnosticInfo = diagnosticInfo;
	}

	/**
	 * Load a trusted list form the specified URL. If the {@code signingCertList} contains any {@code X509Certificate} then the validation of the signature of the TSL is done.
	 *
	 * @param url
	 *            of the TSL to load
	 * @param signingCertList
	 *            the {@code List} of the possible signing certificates
	 * @param diagnosticInfo
	 * @return {@code TrustStatusList}
	 */
	private TrustStatusList getTrustStatusList(final String url, final Set<CertificateToken> signingCertList, DiagnosticInfo diagnosticInfo) {

		boolean refresh = shouldRefresh(url);
		final byte[] bytes = dataLoader.get(url, refresh);

		if (bytes == null) {
			throw new NullPointerException(url);
		}

		boolean coreValidity = !checkSignature || validateTslSignature(signingCertList, bytes, diagnosticInfo);
		final Document doc = DSSXMLUtils.buildDOM(bytes);
		final TrustStatusList trustStatusList = TrustServiceListFactory.newInstance(doc);
		diagnosticInfo.setTslNextUpdate(trustStatusList.getNextUpdate());
		trustStatusList.setWellSigned(coreValidity);
		updateTslNextUpdateDate(url, trustStatusList);
		return trustStatusList;
	}

	private boolean validateTslSignature(final Set<CertificateToken> signingCertList, final byte[] bytes, DiagnosticInfo diagnosticInfo) {
		boolean coreValidity = false;
		if (signingCertList != null) {

			final XMLDocumentValidator xmlDocumentValidator = prepareSignatureValidation(signingCertList, bytes);
			final List<AdvancedSignature> signatures = xmlDocumentValidator.getSignatures();
			if (signatures.size() == 0) {
				throw new DSSException("Not ETSI compliant signature. The Xml is not signed.");
			}
			final Reports reports = xmlDocumentValidator.validateDocument(ValidationResourceManager.class.getResourceAsStream("/policy/tsl-constraint.xml"));
			diagnosticInfo.setUsedSigningCertificatesValidityEndDates(getUsedSigningCertificatesValidityEndDates(xmlDocumentValidator));
			final SimpleReport simpleReport = reports.getSimpleReport();
			final List<String> signatureIdList = simpleReport.getSignatureIdList();
			final String signatureId = signatureIdList.get(0);
			final String indication = simpleReport.getIndication(signatureId);
			coreValidity = "VALID".equals(indication);
			logger.info("The TSL signature validity: " + coreValidity);
			if (!coreValidity) {

				logger.info("The TSL signature validity details:\n" + simpleReport);
				throw new DSSException("Not ETSI compliant signature. The signature is not valid.");
			}
		}
		return coreValidity;
	}

	private static List<Date> getUsedSigningCertificatesValidityEndDates(XMLDocumentValidator xmlDocumentValidator) {
		List<Date> result = new ArrayList<>();
		for(AdvancedSignature signature: xmlDocumentValidator.getSignatures()) {
			result.add(signature.getSigningCertificateToken().getNotAfter());
		}
		return result;
	}

	protected void updateTSLHashCode(final String url, final String currentHashValue) {

		ensureTSLHashCodePropertyFileLoaded();
		tslHashes.setProperty(url, currentHashValue);
		saveProperties(tslHashes, TSL_HASH_PROPERTIES);
	}

	protected String getTSLHashCode(final String url) {

		ensureTSLHashCodePropertyFileLoaded();
		return tslHashes.getProperty(url);
	}

	private void ensureTSLHashCodePropertyFileLoaded() {
		if (tslHashes == null) {
			tslHashes = loadProperties(TSL_HASH_PROPERTIES);
		}
	}

	protected String getTSLNextUpdateDate(final String url) {

		ensureTSLNextUpdatePropertyFileLoaded();
		return tslNextUpdates.getProperty(url);
	}

	protected void updateTslNextUpdateDate(final String url, final TrustStatusList tsl) {

		ensureTSLNextUpdatePropertyFileLoaded();
		final Date nextUpdate = tsl.getNextUpdate();
		tslNextUpdates.setProperty(url, DSSUtils.formatInternal(nextUpdate));
		saveProperties(tslNextUpdates, TSL_NEXT_UPDATE_PROPERTIES);
	}

	private void ensureTSLNextUpdatePropertyFileLoaded() {
		if (tslNextUpdates == null) {
			tslNextUpdates = loadProperties(TSL_NEXT_UPDATE_PROPERTIES);
		}
	}

	private boolean shouldRefresh(final String url) {

		if (tslRefreshPolicy == TSLRefreshPolicy.ALWAYS) {
			return true;
		}
		if (tslRefreshPolicy == TSLRefreshPolicy.NEVER) {
			return false;
		}
		// ETSI TS 119 612 V1.1.1 (2013-06)
		// 6.1 TL publication
		final String urlSha2 = url.substring(0, url.lastIndexOf(".")) + ".sha2";
		boolean refresh = false;
		try {
			final byte[] sha2Bytes = commonsDataLoader.get(urlSha2);
			final String currentHashValue = new String(sha2Bytes).trim();
			if (StringUtils.isBlank(currentHashValue)) {
				throw new DSSException("SHA256 does not exist!");
			}
			final String hashValue = getTSLHashCode(url);
			refresh = (hashValue == null) || !currentHashValue.equals(hashValue);
			if (refresh) {

				updateTSLHashCode(url, currentHashValue);
			}
		} catch (Exception e) {
			if (tslRefreshPolicy == TSLRefreshPolicy.WHEN_NECESSARY_OR_INDETERMINATE) {
				return true;
			}
		}
		// if the current date is after the last known nextUpdate then the refresh is forced.
		final String tslNextUpdateProperty = getTSLNextUpdateDate(url);
		final Date tslNextUpdateDate = DSSUtils.quietlyParseDate(tslNextUpdateProperty);
		if ((tslNextUpdateDate != null) && new Date().after(tslNextUpdateDate)) {
			refresh = true;
		}
		return refresh;
	}

	private XMLDocumentValidator prepareSignatureValidation(final Set<CertificateToken> signingCertList, final byte[] bytes) {

		final CommonTrustedCertificateSource commonTrustedCertificateSource = new CommonTrustedCertificateSource();
		for (final CertificateToken x509Certificate : signingCertList) {

			commonTrustedCertificateSource.addCertificate(x509Certificate);
		}
		final CertificateVerifier certificateVerifier = new CommonCertificateVerifier(true);
		certificateVerifier.setTrustedCertSource(commonTrustedCertificateSource);

		final DSSDocument dssDocument = new InMemoryDocument(bytes);
		final XMLDocumentValidator xmlDocumentValidator = new XMLDocumentValidator(dssDocument);
		xmlDocumentValidator.setCertificateVerifier(certificateVerifier);
		// To increase the security: the default {@code XPathQueryHolder} is used.
		final List<XPathQueryHolder> xPathQueryHolders = xmlDocumentValidator.getXPathQueryHolder();
		xPathQueryHolders.clear();
		final XPathQueryHolder xPathQueryHolder = new XPathQueryHolder();
		xPathQueryHolders.add(xPathQueryHolder);
		return xmlDocumentValidator;
	}

	/**
	 * Load the certificates (trust anchors) contained in all the TSL referenced by the LOTL
	 */
	public void init() {

		if (logger.isInfoEnabled()) {
			logger.info("TSL refresh policy: ", tslRefreshPolicy.name());
			logger.info("TSL property cache folder: ", tslPropertyCacheFolder.getAbsolutePath());
		}

		diagnosticInfo.clear();

		final TrustStatusList lotl = loadLotl();
		final int size = lotl.getOtherTSLPointers().size();

		for (final PointerToOtherTSL pointerToTSL : lotl.getOtherTSLPointers()) {
			final String url = pointerToTSL.getTslLocation();
			final String territory = pointerToTSL.getTerritory();
			final Set<CertificateToken> signingCertList = pointerToTSL.getDigitalIdentity();
			try {

				loadTSL(url, territory, signingCertList);
			} catch (DSSException e) {
				logger.error("Error loading trusted list for {} at {}", new Object[] {
						territory, url, e
				});
			}

		}

		loadAdditionalLists();
		logger.info("Loading completed: {} trusted lists", size);
		logger.info("                 : {} certificates", certPool.getNumberOfCertificates());
	}

	private TrustStatusList loadLotl() {

		CertificateToken lotlCert = null;
		if (checkSignature) {
			List<CertificateToken> trustedCertificatesFromKeyStore = keyStoreCertificateSource.getCertificatesFromKeyStore();
		}
		List<CertificateToken> trustedCertificatesFromKeyStore = keyStoreCertificateSource.getCertificatesFromKeyStore();

		TrustStatusList lotl;
		try {

			logger.info("Downloading LOTL from url= {}", lotlUrl);
			Set<CertificateToken> lotlCertificates = new HashSet<CertificateToken>(trustedCertificatesFromKeyStore);
			lotl = getTrustStatusList(lotlUrl, lotlCertificates, getOrCreateDiagnosticInfoForUrl(lotlUrl));
		} catch (DSSException e) {

			logger.error("The LOTL cannot be loaded: " + e.getMessage(), e);
			throw e;
		}
		getOrCreateDiagnosticInfoForUrl(lotlUrl).setLoadingDiagnosticMessage("Loaded " + new Date().toString());
		return lotl;
	}

	private DiagnosticInfo getOrCreateDiagnosticInfoForUrl(String url) {
		DiagnosticInfo existing = this.diagnosticInfo.get(url);
		if(existing != null) {
			return existing;
		}

		DiagnosticInfo newDiagnosticInfo = new DiagnosticInfo();
		this.diagnosticInfo.put(url, newDiagnosticInfo);
		return newDiagnosticInfo;
	}

	/**
	 * This method gives the possibility to extend this class and to add other trusted lists. It is invoked systematically from {@code #init()} method.
	 *
	 * @param urls
	 */
	protected void loadAdditionalLists(final String... urls) {

	}

	/**
	 * @param url
	 *            of the TSL to load
	 * @param territory
	 *            of the TSL
	 * @param signingCertList
	 *            the {@code List} of the possible signing certificates
	 */
	protected void loadTSL(final String url, final String territory, final Set<CertificateToken> signingCertList) {

		if (StringUtils.isBlank(url)) {

			logger.error("The URL is blank!");
			return;
		}
		final String trimmedUrl = url.trim();
		try {

			getOrCreateDiagnosticInfoForUrl(trimmedUrl).setLoadingDiagnosticMessage("Loading");
			logger.info("Downloading TrustStatusList for '{}' from url='{}'", territory, trimmedUrl);
			final TrustStatusList countryTSL = getTrustStatusList(trimmedUrl, signingCertList, getOrCreateDiagnosticInfoForUrl(trimmedUrl));
			loadAllCertificatesFromOneTSL(countryTSL);
			logger.info(".... done for '{}'", territory);
			getOrCreateDiagnosticInfoForUrl(trimmedUrl).setLoadingDiagnosticMessage("Loaded " + new Date().toString());
		} catch (final Exception e) {
			logger.error("An error occurred while loading url " + url + " : " + e.getMessage(), e);
			getOrCreateDiagnosticInfoForUrl(url).setLoadingDiagnosticMessage("Unable to load TSL : " + e.getMessage());
		}
	}

	/**
	 * Adds all the service entries (current and history) of all the providers of the trusted list to the list of
	 * CertificateSource
	 *
	 * @param trustStatusList
	 */
	private void loadAllCertificatesFromOneTSL(final TrustStatusList trustStatusList) {

		for (final TrustServiceProvider trustServiceProvider : trustStatusList.getTrustServicesProvider()) {

			for (final AbstractTrustService trustService : trustServiceProvider.getTrustServiceList()) {

				if (logger.isTraceEnabled()) {
					logger.trace("#Service Name: " + trustService.getServiceName());
					logger.trace("      ------> " + trustService.getType());
					logger.trace("      ------> " + trustService.getStatus());
				}
				for (final Object digitalIdentity : trustService.getDigitalIdentity()) {
					try {
						CertificateToken certificateToken = null;
						if (digitalIdentity instanceof CertificateToken) {
							certificateToken = (CertificateToken) digitalIdentity;
						} else if (digitalIdentity instanceof X500Principal) {
							final X500Principal x500Principal = (X500Principal) digitalIdentity;
							final List<CertificateToken> certificateTokens = certPool.get(x500Principal);
							if (certificateTokens.size() > 0) {
								certificateToken = certificateTokens.get(0);
							} else {
								logger.debug("WARNING: There is currently no certificate with the given X500Principal: '{}' within the certificate pool!", x500Principal);
							}
						}
						if (certificateToken != null) {
							addCertificate(certificateToken, trustService, trustServiceProvider, trustStatusList.isWellSigned());
						}
					} catch (DSSException e) {
						// There is a problem when loading the certificate, we continue with the next one.
						logger.warn(e.getMessage());
					}
				}

				for (String certificateUri : trustService.getCertificateUrls()) {
					try {
						logger.debug("Try to load certificate from URI : " + certificateUri);
						byte[] certBytes = dataLoader.get(certificateUri);
						if (ArrayUtils.isNotEmpty(certBytes)) {
							CertificateToken certificateToken = DSSUtils.loadCertificate(certBytes);
							if (certificateToken != null) {
								addCertificate(certificateToken, trustService, trustServiceProvider, trustStatusList.isWellSigned());
							}
						}
					} catch (DSSException e) {
						logger.warn("Unable to add certificate '" + certificateUri + "' : " + e.getMessage());
					}
				}

			}
		}
	}

	/**
	 * This method allows to set the {@code RefreshPolicy} to be used when loading or re-loading the trusted lists.
	 *
	 * @param tslRefreshPolicy
	 *            {@code RefreshPolicy} to use
	 */
	public void setTslRefreshPolicy(final TSLRefreshPolicy tslRefreshPolicy) {
		this.tslRefreshPolicy = tslRefreshPolicy;
	}

	/**
	 +	 * This method allows to set a KeyStoreCertificateSource
	 +	 * @param keyStoreCertificateSource
	 +	 */
	public void setKeyStoreCertificateSource(KeyStoreCertificateSource keyStoreCertificateSource) {
		this.keyStoreCertificateSource = keyStoreCertificateSource;
	}

	/**
	 * Defines if the TL signature must be checked.
	 *
	 * @param checkSignature
	 *            the checkSignature to set
	 */
	public void setCheckSignature(final boolean checkSignature) {
		this.checkSignature = checkSignature;
	}

	/**
	 * Define the URL of the LOTL
	 *
	 * @param lotlUrl
	 *            the lotlUrl to set
	 */
	public void setLotlUrl(final String lotlUrl) {
		this.lotlUrl = lotlUrl;
	}

	/**
	 * @param dataLoader
	 *            the dataLoader to set
	 */
	public void setDataLoader(final DataLoader dataLoader) {

		this.dataLoader = dataLoader;
		if (dataLoader instanceof CommonsDataLoader) {

			CommonsDataLoader commonsDataLoader1 = (CommonsDataLoader) dataLoader;
			commonsDataLoader.setProxyPreferenceManager(commonsDataLoader1.getProxyPreferenceManager());
			commonsDataLoader1.propagateAuthentication(commonsDataLoader);
		}
	}

	/**
	 * @param tslPropertyCacheFolder
	 */
	public void setTslPropertyCacheFolder(final File tslPropertyCacheFolder) {
		this.tslPropertyCacheFolder = tslPropertyCacheFolder;
	}

	/**
	 * @param propertiesFileName
	 * @return
	 */
	public Properties loadProperties(final String propertiesFileName) {

		final Properties properties = new Properties();
		final File file = new File(tslPropertyCacheFolder, propertiesFileName);
		if (file.exists()) {
			try {
				final InputStream inputStream = DSSUtils.toInputStream(file);
				properties.load(inputStream);
			} catch (Exception e) {
				logger.error("Impossible to load: '{}'", file.getAbsolutePath(), e);
			}
		}
		return properties;
	}

	/**
	 * @param properties
	 * @param propertiesFileName
	 */
	public void saveProperties(final Properties properties, final String propertiesFileName) {

		final File file = new File(tslPropertyCacheFolder, propertiesFileName);
		try {

			final FileOutputStream fileOutputStream = new FileOutputStream(file);
			properties.store(fileOutputStream, null);
		} catch (Exception e) {
			logger.error("Impossible to save: '{}'", file.getAbsolutePath(), e);
		}
	}

	public static class DiagnosticInfo {
		private String loadingDiagnosticInfo;
		private List<Date> usedSigningCertificatesValidityEndDates;
		private Date tslNextUpdate;

		public void setLoadingDiagnosticMessage(String loadingDiagnosticInfo) {
			this.loadingDiagnosticInfo = loadingDiagnosticInfo;
		}

		public String getLoadingDiagnosticInfo() {
			return loadingDiagnosticInfo;
		}

		public void setUsedSigningCertificatesValidityEndDates(List<Date> usedSigningCertificatesValidityEndDates) {
			this.usedSigningCertificatesValidityEndDates = usedSigningCertificatesValidityEndDates;
		}

		public List<Date> getUsedSigningCertificatesValidityEndDates() {
			return usedSigningCertificatesValidityEndDates;
		}

		public void setTslNextUpdate(Date tslNextUpdate) {
			this.tslNextUpdate = tslNextUpdate;
		}

		public Date getTslNextUpdate() {
			return tslNextUpdate;
		}
	}
}
