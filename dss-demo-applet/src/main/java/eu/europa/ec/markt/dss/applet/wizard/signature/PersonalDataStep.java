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
package eu.europa.ec.markt.dss.applet.wizard.signature;

import eu.europa.ec.markt.dss.DSSUtils;
import eu.europa.ec.markt.dss.applet.main.Parameters;
import eu.europa.ec.markt.dss.applet.model.SignatureModel;
import eu.europa.ec.markt.dss.commons.swing.mvc.applet.ControllerException;
import eu.europa.ec.markt.dss.commons.swing.mvc.applet.wizard.WizardStep;
import eu.europa.ec.markt.dss.commons.swing.mvc.applet.wizard.WizardView;

/**
 * 
 * TODO
 * 
 *
 *
 * 
 *
 *
 */
public class PersonalDataStep extends WizardStep<SignatureModel, SignatureWizardController> {
    /**
     * 
     * The default constructor for PersonalDataStep.
     * 
     * @param model
     * @param view
     * @param controller
     */
    public PersonalDataStep(final SignatureModel model, final WizardView<SignatureModel, SignatureWizardController> view, final SignatureWizardController controller) {
        super(model, view, controller);
    }

    /*
     * (non-Javadoc)
     * 
     * @see eu.europa.ec.markt.dss.commons.swing.mvc.applet.wizard.WizardStep#finish()
     */
    @Override
    protected void finish() throws ControllerException {

    }

    /*
     * (non-Javadoc)
     * 
     * @see eu.europa.ec.markt.dss.commons.swing.mvc.applet.wizard.WizardStep#getBackStep()
     */
    @Override
    protected Class<? extends WizardStep<SignatureModel, SignatureWizardController>> getBackStep() {
        return CertificateStep.class;
    }

    /*
     * (non-Javadoc)
     * 
     * @see eu.europa.ec.markt.dss.commons.swing.mvc.applet.wizard.WizardStep#getNextStep()
     */
    @Override
    protected Class<? extends WizardStep<SignatureModel, SignatureWizardController>> getNextStep() {
        return SaveStep.class;
    }

    /*
     * (non-Javadoc)
     * 
     * @see eu.europa.ec.markt.dss.commons.swing.mvc.applet.wizard.WizardStep#getStepProgression()
     */
    @Override
    protected int getStepProgression() {
        return 5;
    }

    /*
     * (non-Javadoc)
     * 
     * @see eu.europa.ec.markt.dss.commons.swing.mvc.applet.wizard.WizardStep#execute()
     */
    @Override
    protected void init() {

        final Parameters parameters = getController().getParameter();
        final SignatureModel model = getModel();

        if (parameters.hasSignaturePolicyAlgo() && DSSUtils.isEmpty(model.getSignaturePolicyAlgo())) {
            model.setSignaturePolicyAlgo(parameters.getSignaturePolicyAlgo());
        }

        if (parameters.hasSignaturePolicyValue() && DSSUtils.isEmpty(model.getSignaturePolicyValue())) {
            model.setSignaturePolicyValue(DSSUtils.base64Encode(parameters.getSignaturePolicyValue()));
        }

        // TODO: (Bob: 2014 Jan 19) To be adapted to baseline profile
        final boolean levelBES = model.getLevel().toUpperCase().endsWith("-BES");
        model.setSignaturePolicyVisible(!levelBES);

    }

    /*
     * (non-Javadoc)
     * 
     * @see eu.europa.ec.markt.dss.commons.swing.mvc.applet.wizard.WizardStep#isValid()
     */
    @Override
    protected boolean isValid() {

        final SignatureModel model = getModel();

        if (model.isSignaturePolicyCheck()) {
            return DSSUtils.isNotEmpty(model.getSignaturePolicyAlgo()) && DSSUtils.isNotEmpty(model.getSignaturePolicyId()) && DSSUtils.isNotEmpty(model.getSignaturePolicyValue());
        }
        return true;
    }
}
