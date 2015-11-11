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
package eu.europa.ec.markt.dss.validation102853.tsl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.Security;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.security.auth.x500.X500Principal;
import org.apache.commons.lang.StringUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import eu.europa.ec.markt.dss.DSSUtils;
import eu.europa.ec.markt.dss.DSSXMLUtils;
import eu.europa.ec.markt.dss.exception.DSSException;
import eu.europa.ec.markt.dss.exception.DSSNotApplicableMethodException;
import eu.europa.ec.markt.dss.exception.DSSNotETSICompliantException;
import eu.europa.ec.markt.dss.exception.DSSNullReturnedException;
import eu.europa.ec.markt.dss.signature.DSSDocument;
import eu.europa.ec.markt.dss.signature.InMemoryDocument;
import eu.europa.ec.markt.dss.signature.validation.AdvancedSignature;
import eu.europa.ec.markt.dss.validation102853.CertificateToken;
import eu.europa.ec.markt.dss.validation102853.CertificateVerifier;
import eu.europa.ec.markt.dss.validation102853.CommonCertificateVerifier;
import eu.europa.ec.markt.dss.validation102853.CommonTrustedCertificateSource;
import eu.europa.ec.markt.dss.validation102853.KeyStoreCertificateSource;
import eu.europa.ec.markt.dss.validation102853.ValidationResourceManager;
import eu.europa.ec.markt.dss.validation102853.certificate.CertificateSourceType;
import eu.europa.ec.markt.dss.validation102853.condition.ServiceInfo;
import eu.europa.ec.markt.dss.validation102853.https.CommonsDataLoader;
import eu.europa.ec.markt.dss.validation102853.loader.DataLoader;
import eu.europa.ec.markt.dss.validation102853.report.Reports;
import eu.europa.ec.markt.dss.validation102853.report.SimpleReport;
import eu.europa.ec.markt.dss.validation102853.rules.Indication;
import eu.europa.ec.markt.dss.validation102853.xades.XMLDocumentValidator;
import eu.europa.ec.markt.dss.validation102853.xades.XPathQueryHolder;

/**
 * This class allows to extract all the trust anchors defined by the trusted lists. The LOTL is used as the entry point of the process.
 *
 *
 */

public class TrustedListsCertificateSource extends CommonTrustedCertificateSource {

	private static final Logger LOG = LoggerFactory.getLogger(TrustedListsCertificateSource.class);

	public static final String TSL_HASH_PROPERTIES = "tsl_hash.properties";
	public static final String TSL_NEXT_UPDATE_PROPERTIES = "tsl_next_update.properties";

	private File tslPropertyCacheFolder = new File(System.getProperty("java.io.tmpdir"));
	private Properties tslHashes = null;
	private Properties tslNextUpdates = null;

	protected TSLRefreshPolicy tslRefreshPolicy = TSLRefreshPolicy.ALWAYS;

	private CommonsDataLoader commonsDataLoader = new CommonsDataLoader();

	protected String lotlUrl;

	protected transient DataLoader dataLoader;

	private Map<String, String> diagnosticInfo = new HashMap<String, String>();

	/**
	 * Defines if the TL signature must be checked. The default value is true.
	 */
	protected boolean checkSignature = true;

	private KeyStoreCertificateSource keyStoreCertificateSource;

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
	 * This method is not applicable for this kind of certificate source. You should use {@link
	 * #addCertificate(java.security.cert.X509Certificate, eu.europa.ec.markt.dss.validation102853.condition.ServiceInfo)}
	 *
	 * @param x509Certificate the certificate you have to trust
	 * @return the corresponding certificate token
	 */
	@Override
	public CertificateToken addCertificate(final CertificateToken x509Certificate) {

		throw new DSSNotApplicableMethodException(getClass());
	}

	/**
	 * Adds a service entry (current or history) to the list of certificate tokens.
	 *
	 * @param x509Certificate the certificate which identifies the trusted service
	 * @param trustedService  Object defining the trusted service
	 * @param tsProvider      Object defining the trusted service provider, must be the parent of the trusted service
	 * @param tlWellSigned    Indicates if the signature of trusted list is valid
	 */
	private synchronized void addCertificate(final CertificateToken x509Certificate, final AbstractTrustService trustedService, final TrustServiceProvider tsProvider,
			final boolean tlWellSigned) {

		try {
			final ServiceInfo serviceInfo = getServiceInfo(trustedService, tsProvider, tlWellSigned);
			addCertificate(x509Certificate, serviceInfo);
		} catch (DSSNotETSICompliantException ex) {

			LOG.error("The entry for " + trustedService.getServiceName() + " doesn't respect ETSI specification " + ex.getLocalizedMessage());
		}
	}

	/**
	 * This method return the service info object enclosing the certificate.
	 *
	 * @param trustedService Object defining the trusted service
	 * @param tsProvider     Object defining the trusted service provider, must be the parent of the trusted service
	 * @param tlWellSigned   Indicates if the signature of trusted list is valid
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

		return Collections.unmodifiableMap(diagnosticInfo);
	}

	/**
	 * Load a trusted list form the specified URL. If the {@code signingCertList} contains any {@code X509Certificate} then the validation of the signature of the TSL is done.
	 *
	 * @param url             of the TSL to load
	 * @param signingCertList the {@code List} of the possible signing certificates
	 * @return {@code TrustStatusList}
	 */
	private TrustStatusList getTrustStatusList(final String url, final Set<CertificateToken> signingCertList) {

		boolean refresh = shouldRefresh(url);
		final byte[] bytes = dataLoader.get(url, refresh);
		if (bytes == null) {
			throw new DSSNullReturnedException(url);
		}
		boolean coreValidity = !checkSignature || validateTslSignature(signingCertList, bytes);
		final Document doc = DSSXMLUtils.buildDOM(bytes);
		final TrustStatusList trustStatusList = TrustServiceListFactory.newInstance(doc);
		trustStatusList.setWellSigned(coreValidity);
		updateTslNextUpdateDate(url, trustStatusList);
		return trustStatusList;
	}

	private boolean validateTslSignature(final Set<CertificateToken> signingCertList, final byte[] bytes) {

		boolean coreValidity = false;
		if (signingCertList != null) {

			final XMLDocumentValidator xmlDocumentValidator = prepareSignatureValidation(signingCertList, bytes);
			final List<AdvancedSignature> signatures = xmlDocumentValidator.getSignatures();
			if (signatures.size() == 0) {
				throw new DSSException("Not ETSI compliant signature. The Xml is not signed.");
			}
			final Reports reports = xmlDocumentValidator
					.validateDocument(ValidationResourceManager.class
							.getResourceAsStream(ValidationResourceManager.tslPolicyConstraintsLocation));
			final SimpleReport simpleReport = reports.getSimpleReport();
			final List<String> signatureIdList = simpleReport.getSignatureIdList();
			final String signatureId = signatureIdList.get(0);
			final String indication = simpleReport.getIndication(signatureId);
			coreValidity = Indication.VALID.equals(indication);
			LOG.info("The TSL signature validity: " + coreValidity);
			if (!coreValidity) {

				LOG.info("The TSL signature validity details:\n" + simpleReport);
				throw new DSSException("Not ETSI compliant signature. The signature is not valid.");
			}
		}
		return coreValidity;
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
			if (DSSUtils.isBlank(currentHashValue)) {
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

		if (LOG.isInfoEnabled()) {
			LOG.info("TSL refresh policy: ", tslRefreshPolicy.name());
			LOG.info("TSL property cache folder: ", tslPropertyCacheFolder.getAbsolutePath());
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
				LOG.error("Error loading trusted list for {} at {}", new Object[]{territory, url, e});
			}

		}

		loadAdditionalLists();
		LOG.info("Loading completed: {} trusted lists", size);
		LOG.info("                 : {} certificates", certPool.getNumberOfCertificates());
	}

	private TrustStatusList loadLotl() {
		List<CertificateToken> trustedCertificatesFromKeyStore = keyStoreCertificateSource.getCertificatesFromKeyStore();
		TrustStatusList lotl;
		try {

			LOG.info("Downloading LOTL from url= {}", lotlUrl);
			final Set<CertificateToken> lotlCertificates = new HashSet<CertificateToken>(trustedCertificatesFromKeyStore);
			lotl = getTrustStatusList(lotlUrl, lotlCertificates);
		} catch (DSSException e) {

			LOG.error("The LOTL cannot be loaded: " + e.getMessage(), e);
			throw e;
		}
		diagnosticInfo.put(lotlUrl, "Loaded " + new Date().toString());
		return lotl;
	}

	/**
	 * This method gives the possibility to extend this class and to add other trusted lists. It is invoked systematically from {@code #init()} method.
	 *
	 * @param urls
	 */
	protected void loadAdditionalLists(final String... urls) {

	}

	/**
	 * @param url             of the TSL to load
	 * @param territory       of the TSL
	 * @param signingCertList the {@code List} of the possible signing certificates
	 */
	protected void loadTSL(final String url, final String territory, final Set<CertificateToken> signingCertList) {

		if (StringUtils.isBlank(url)) {

			LOG.error("The URL is blank!");
			return;
		}
		final String trimmedUrl = url.trim();
		try {

			diagnosticInfo.put(trimmedUrl, "Loading");
			LOG.info("Downloading TrustStatusList for '{}' from url='{}'", territory, trimmedUrl);
			final TrustStatusList countryTSL = getTrustStatusList(trimmedUrl, signingCertList);
			loadAllCertificatesFromOneTSL(countryTSL);
			LOG.info(".... done for '{}'", territory);
			diagnosticInfo.put(trimmedUrl, "Loaded " + new Date().toString());
		} catch (final DSSNullReturnedException e) {

			LOG.info("Download skipped.");
			// do nothing: it can happened when a mock data loader is used.
		} catch (final Exception e) {
			makeATrace(trimmedUrl, "Other problem: " + e.toString(), e);
		}
	}

	private void makeATrace(final String url, final String message, final Exception e) {

		LOG.error(message, e);
		StringWriter w = new StringWriter();
		e.printStackTrace(new PrintWriter(w));
		diagnosticInfo.put(url, w.toString());
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

				if (LOG.isTraceEnabled()) {
					LOG.trace("#Service Name: " + trustService.getServiceName());
					LOG.trace("      ------> " + trustService.getType());
					LOG.trace("      ------> " + trustService.getStatus());
				}
				for (final Object digitalIdentity : trustService.getDigitalIdentity()) {

					try {

						CertificateToken x509Certificate = null;
						if (digitalIdentity instanceof CertificateToken) {

							x509Certificate = (CertificateToken) digitalIdentity;
						} else if (digitalIdentity instanceof X500Principal) {

							final X500Principal x500Principal = (X500Principal) digitalIdentity;
							final List<CertificateToken> certificateTokens = certPool.get(x500Principal);
							if (certificateTokens.size() > 0) {
								x509Certificate = certificateTokens.get(0);
							} else {
								LOG.debug("WARNING: There is currently no certificate with the given X500Principal: '{}' within the certificate pool!", x500Principal);
							}
						}
						if (x509Certificate != null) {

							addCertificate(x509Certificate, trustService, trustServiceProvider, trustStatusList.isWellSigned());
						}
					} catch (DSSException e) {

						// There is a problem when loading the certificate, we continue with the next one.
						LOG.warn(e.getLocalizedMessage());
					}
				}
			}
		}
	}

	/**
	 * This method allows to set the {@code RefreshPolicy} to be used when loading or re-loading the trusted lists.
	 *
	 * @param tslRefreshPolicy {@code RefreshPolicy} to use
	 */
	public void setTslRefreshPolicy(final TSLRefreshPolicy tslRefreshPolicy) {
		this.tslRefreshPolicy = tslRefreshPolicy;
	}

	/**
	 * Defines if the TL signature must be checked.
	 *
	 * @param checkSignature the checkSignature to set
	 */
	public void setCheckSignature(final boolean checkSignature) {
		this.checkSignature = checkSignature;
	}

	/**
	 * This method allows to set a KeyStoreCertificateSource
	 * @param keyStoreCertificateSource
	 */
	public void setKeyStoreCertificateSource(KeyStoreCertificateSource keyStoreCertificateSource) {
		this.keyStoreCertificateSource = keyStoreCertificateSource;
	}

	/**
	 * Define the URL of the LOTL
	 *
	 * @param lotlUrl the lotlUrl to set
	 */
	public void setLotlUrl(final String lotlUrl) {
		this.lotlUrl = lotlUrl;
	}

	/**
	 * @param dataLoader the dataLoader to set
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
				LOG.error("Impossible to load: '{}'", file.getAbsolutePath(), e);
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
			LOG.error("Impossible to save: '{}'", file.getAbsolutePath(), e);
		}
	}
	
}
