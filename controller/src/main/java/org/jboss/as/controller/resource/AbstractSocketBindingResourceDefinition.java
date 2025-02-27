/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.resource;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.MaskedAddressValidator;
import org.jboss.as.controller.operations.validation.MulticastAddressValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for a resource representing a socket binding.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public abstract class AbstractSocketBindingResourceDefinition extends SimpleResourceDefinition {


    public static final String SOCKET_BINDING_CAPABILITY_NAME = "org.wildfly.network.socket-binding";

    // Common attributes

    public static final PathElement PATH = PathElement.pathElement(ModelDescriptionConstants.SOCKET_BINDING);

    public static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.NAME, ModelType.STRING, false)
            .setResourceOnly()
            .setValidator(new StringLengthValidator(1)).build();

    public static final SimpleAttributeDefinition INTERFACE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.INTERFACE, ModelType.STRING, true)
            .setAllowExpression(true)
            .setExpressionsDeprecated()
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setCapabilityReference("org.wildfly.network.interface", SOCKET_BINDING_CAPABILITY_NAME)
            .build();

    public static final SimpleAttributeDefinition PORT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PORT, ModelType.INT, true)
            .setAllowExpression(true)
            .setValidator(new IntRangeValidator(0, 65535, true, true))
            .setDefaultValue(ModelNode.ZERO)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES).build();

    public static final SimpleAttributeDefinition FIXED_PORT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.FIXED_PORT, ModelType.BOOLEAN, true)
            .setAllowExpression(true).setDefaultValue(ModelNode.FALSE)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .build();

    public static final SimpleAttributeDefinition MULTICAST_ADDRESS = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.MULTICAST_ADDRESS, ModelType.STRING, true)
            .setAllowExpression(true).setValidator(new MulticastAddressValidator(true, true))
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES).build();

    public static final SimpleAttributeDefinition MULTICAST_PORT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.MULTICAST_PORT, ModelType.INT, true)
            .setAllowExpression(true).setValidator(new IntRangeValidator(1, 65535, true, true))
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES).build();

    public static final SimpleAttributeDefinition CLIENT_MAPPING_SOURCE_NETWORK =
            new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SOURCE_NETWORK, ModelType.STRING, true)
            .setAllowExpression(true)
            .setValidator(new MaskedAddressValidator(true, true))
            .build();

    public static final SimpleAttributeDefinition CLIENT_MAPPING_DESTINATION_ADDRESS =
            new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.DESTINATION_ADDRESS, ModelType.STRING, false)
            .setAllowExpression(true)
            .build();

    public static final SimpleAttributeDefinition CLIENT_MAPPING_DESTINATION_PORT =
            new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.DESTINATION_PORT, ModelType.INT, true)
            .setAllowExpression(true)
            .setValidator(new IntRangeValidator(0, 65535, true, true))
            .build();

    private static final ObjectTypeAttributeDefinition CLIENT_MAPPING = ObjectTypeAttributeDefinition.Builder.of("client-mapping", CLIENT_MAPPING_SOURCE_NETWORK, CLIENT_MAPPING_DESTINATION_ADDRESS, CLIENT_MAPPING_DESTINATION_PORT).build();

    public static final AttributeDefinition CLIENT_MAPPINGS = ObjectListAttributeDefinition.Builder.of(ModelDescriptionConstants.CLIENT_MAPPINGS, CLIENT_MAPPING)
            .setRequired(false)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .build();

    public AbstractSocketBindingResourceDefinition(final OperationStepHandler addHandler, final OperationStepHandler removeHandler, RuntimeCapability<Void>... capabilities) {
        super(new Parameters(PATH, ControllerResolver.getResolver(ModelDescriptionConstants.SOCKET_BINDING))
                .setAddHandler(addHandler)
                .setRemoveHandler(removeHandler)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_ALL_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_ALL_SERVICES)
                .addCapabilities(capabilities));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(NAME, null);
        resourceRegistration.registerReadWriteAttribute(INTERFACE, null, getInterfaceWriteAttributeHandler());
        resourceRegistration.registerReadWriteAttribute(PORT, null, getPortWriteAttributeHandler());
        resourceRegistration.registerReadWriteAttribute(FIXED_PORT, null, getFixedPortWriteAttributeHandler());
        resourceRegistration.registerReadWriteAttribute(MULTICAST_ADDRESS, null, getMulticastAddressWriteAttributeHandler());
        resourceRegistration.registerReadWriteAttribute(MULTICAST_PORT, null, getMulticastPortWriteAttributeHandler());
        resourceRegistration.registerReadWriteAttribute(CLIENT_MAPPINGS, null, getClientMappingsWriteAttributeHandler());

    }

    protected abstract OperationStepHandler getInterfaceWriteAttributeHandler();
    protected abstract OperationStepHandler getPortWriteAttributeHandler();
    protected abstract OperationStepHandler getFixedPortWriteAttributeHandler();
    protected abstract OperationStepHandler getMulticastAddressWriteAttributeHandler();
    protected abstract OperationStepHandler getMulticastPortWriteAttributeHandler();
    protected abstract OperationStepHandler getClientMappingsWriteAttributeHandler();
}
