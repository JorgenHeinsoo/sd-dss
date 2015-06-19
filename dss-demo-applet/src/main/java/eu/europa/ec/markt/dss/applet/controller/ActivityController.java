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
package eu.europa.ec.markt.dss.applet.controller;

import eu.europa.ec.markt.dss.applet.main.DSSAppletCore;
import eu.europa.ec.markt.dss.applet.model.ActivityModel;
import eu.europa.ec.markt.dss.applet.view.ActivityView;
import eu.europa.ec.markt.dss.applet.wizard.extension.ExtensionWizardController;
import eu.europa.ec.markt.dss.applet.wizard.signature.SignatureWizardController;
import eu.europa.ec.markt.dss.applet.wizard.validation.ValidationWizardController;
import eu.europa.ec.markt.dss.applet.wizard.validationpolicy.ValidationPolicyWizardController;

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
public class ActivityController extends DSSAppletController<ActivityModel> {

    private ActivityView view;

    /**
     * 
     * The default constructor for ActivityController.
     * 
     * @param core
     * @param model
     */
    public ActivityController(final DSSAppletCore core, final ActivityModel model) {
        super(core, model);
        view = new ActivityView(getCore(), this, getModel());
    }

    /**
     * 
     */
    public void display() {
        view.show();
    }

    /**
     * 
     */
    public void startAction() {
        switch (getModel().getAction()) {
        case EXTEND:
            getCore().getController(ExtensionWizardController.class).start();
            break;
        case SIGN:
            getCore().getController(SignatureWizardController.class).start();
            break;
        case VERIFY:
            getCore().getController(ValidationWizardController.class).start();
            break;
        case EDIT_VALIDATION_POLICY:
            getCore().getController(ValidationPolicyWizardController.class).start();
            break;
        }
    }
}
