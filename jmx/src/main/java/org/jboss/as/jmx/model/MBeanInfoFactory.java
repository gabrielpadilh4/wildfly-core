/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx.model;

import static javax.management.JMX.DEFAULT_VALUE_FIELD;
import static javax.management.JMX.LEGAL_VALUES_FIELD;
import static javax.management.JMX.MAX_VALUE_FIELD;
import static javax.management.JMX.MIN_VALUE_FIELD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALLOWED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEFAULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIBE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXPRESSIONS_ALLOWED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MAX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MIN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_TYPES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_OPERATION_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_OPERATION_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REPLY_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REQUEST_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.AttributeChangeNotification;
import javax.management.Descriptor;
import javax.management.ImmutableDescriptor;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.Notification;
import javax.management.ObjectName;
import javax.management.openmbean.OpenMBeanAttributeInfo;
import javax.management.openmbean.OpenMBeanAttributeInfoSupport;
import javax.management.openmbean.OpenMBeanConstructorInfo;
import javax.management.openmbean.OpenMBeanInfoSupport;
import javax.management.openmbean.OpenMBeanOperationInfo;
import javax.management.openmbean.OpenMBeanOperationInfoSupport;
import javax.management.openmbean.OpenMBeanParameterInfo;
import javax.management.openmbean.OpenMBeanParameterInfoSupport;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CompositeOperationHandler;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.ValidateAddressOperationHandler;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.AttributeAccess.AccessType;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.NotificationEntry;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.OperationEntry.Flag;
import org.jboss.as.jmx.logging.JmxLogger;
import org.jboss.as.jmx.model.ChildAddOperationFinder.ChildAddOperationEntry;
import org.jboss.as.server.deployment.DeploymentUploadStreamAttachmentHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class MBeanInfoFactory {

    private static final String DESC_MBEAN_EXPR = "mbean.expression.support";
    private static final String DESC_MBEAN_EXPR_DESCR = "mbean.expression.support.description";
    private static final String DESC_ALTERNATE_MBEAN = "alternate.mbean";
    private static final String DESC_ALTERNATE_MBEAN_DESCR = "alternate.mbean.description";
    private static final String DESC_EXPRESSIONS_ALLOWED = "expressions.allowed";
    private static final String DESC_EXPRESSIONS_ALLOWED_DESC = "expressions.allowed.description";

    private static final OpenMBeanParameterInfo[] EMPTY_PARAMETERS = new OpenMBeanParameterInfo[0];
    private final ObjectName name;
    private final TypeConverters converters;
    private final ConfiguredDomains configuredDomains;
    private final MutabilityChecker mutabilityChecker;
    private final ImmutableManagementResourceRegistration resourceRegistration;
    private final ModelNode providedDescription;
    private final PathAddress pathAddress;
    private final boolean legacy;

    private MBeanInfoFactory(final ObjectName name, final TypeConverters converters, final ConfiguredDomains configuredDomains, final MutabilityChecker mutabilityChecker, final PathAddress address, final ImmutableManagementResourceRegistration resourceRegistration) {
        this.name = name;
        this.converters = converters;
        this.configuredDomains = configuredDomains;
        this.mutabilityChecker = mutabilityChecker;
        this.legacy = configuredDomains.isLegacyDomain(name);
        this.resourceRegistration = resourceRegistration;
        DescriptionProvider provider = resourceRegistration.getModelDescription(PathAddress.EMPTY_ADDRESS);
        providedDescription = provider != null ? provider.getModelDescription(null) : new ModelNode();
        this.pathAddress = address;
    }

    static MBeanInfo createMBeanInfo(final ObjectName name, final TypeConverters converters, final ConfiguredDomains configuredDomains, final MutabilityChecker mutabilityChecker, final PathAddress address, final ImmutableManagementResourceRegistration resourceRegistration) throws InstanceNotFoundException{
        return new MBeanInfoFactory(name, converters, configuredDomains, mutabilityChecker, address, resourceRegistration).createMBeanInfo();
    }

    private MBeanInfo createMBeanInfo() {
        return new OpenMBeanInfoSupport(ModelControllerMBeanHelper.CLASS_NAME,
                getDescription(providedDescription),
                getAttributes(),
                getConstructors(),
                getOperations(),
                getNotifications(),
                createMBeanDescriptor());
    }


    private String getDescription(ModelNode node) {
        if (!node.hasDefined(DESCRIPTION)) {
            return "-";
        }
        String description = node.get(DESCRIPTION).asString();
        if (description.trim().length() == 0) {
            return "-";
        }
        return description;
    }

    private OpenMBeanAttributeInfo[] getAttributes() {
        List<OpenMBeanAttributeInfo> infos = new LinkedList<OpenMBeanAttributeInfo>();
        if (providedDescription.hasDefined(ATTRIBUTES)) {
            for (final String name : providedDescription.require(ATTRIBUTES).keys()) {
                OpenMBeanAttributeInfo attributeInfo = getAttribute(name);
                if (attributeInfo != null) {
                    infos.add(getAttribute(name));
                }
            }
        }
        return infos.toArray(new OpenMBeanAttributeInfo[infos.size()]);
    }

    private OpenMBeanAttributeInfo getAttribute(String name) {
        final String escapedName = NameConverter.convertToCamelCase(name);
        ModelNode attribute = providedDescription.require(ATTRIBUTES).require(name);
        AttributeAccess access = resourceRegistration.getAttributeAccess(PathAddress.EMPTY_ADDRESS, name);
        if (access == null) {
            return null; // access is needed to create a new OpenMBeanAttributeInfoSupport object
        }
        final boolean writable = mutabilityChecker.mutable(pathAddress) && (access.getAccessType() == AccessType.READ_WRITE);

        return new OpenMBeanAttributeInfoSupport(
                escapedName,
                getDescription(attribute),
                converters.convertToMBeanType(access.getAttributeDefinition(), attribute),
                true,
                writable,
                false,
                createAttributeDescriptor(attribute));
    }

    private OpenMBeanConstructorInfo[] getConstructors() {
        //This can be left empty
        return null;
    }

    private OpenMBeanOperationInfo[] getOperations() {
        final boolean root = pathAddress.size() == 0;

        //TODO include inherited/global operations?
        List<OpenMBeanOperationInfo> ops = new ArrayList<OpenMBeanOperationInfo>();
        for (Map.Entry<String, OperationEntry> entry : resourceRegistration.getOperationDescriptions(PathAddress.EMPTY_ADDRESS, false).entrySet()) {
            final String opName = entry.getKey();
            if (opName.equals(ADD) || opName.equals(DESCRIBE)) {
                continue;
            }
            if (root) {
                if (opName.equals(READ_RESOURCE_OPERATION) || opName.equals(READ_ATTRIBUTE_OPERATION) ||
                        opName.equals(READ_RESOURCE_DESCRIPTION_OPERATION) || opName.equals(READ_CHILDREN_NAMES_OPERATION) ||
                        opName.equals(READ_CHILDREN_TYPES_OPERATION) || opName.equals(READ_CHILDREN_RESOURCES_OPERATION) ||
                        opName.equals(READ_OPERATION_NAMES_OPERATION) || opName.equals(READ_OPERATION_DESCRIPTION_OPERATION) ||
                        opName.equals(WRITE_ATTRIBUTE_OPERATION) || opName.equals(ValidateAddressOperationHandler.OPERATION_NAME) ||
                        opName.equals(CompositeOperationHandler.NAME) || opName.equals(DeploymentUploadStreamAttachmentHandler.OPERATION_NAME)) {
                    //Ignore some of the global operations which probably don't make much sense here
                    continue;
                }
            }
            final OperationEntry opEntry = entry.getValue();
            if (mutabilityChecker.mutable(pathAddress) || opEntry.getFlags().contains(Flag.READ_ONLY) || opEntry.getFlags().contains(Flag.RUNTIME_ONLY)) {
                ops.add(getOperation(NameConverter.convertToCamelCase(entry.getKey()), null, opEntry));
            }
        }
        addChildAddOperations(ops, resourceRegistration);
        return ops.toArray(new OpenMBeanOperationInfo[ops.size()]);
    }

    private void addChildAddOperations(List<OpenMBeanOperationInfo> ops, ImmutableManagementResourceRegistration resourceRegistration) {
        for (Map.Entry<PathElement, ChildAddOperationEntry> entry : ChildAddOperationFinder.findAddChildOperations(pathAddress, mutabilityChecker, resourceRegistration).entrySet()) {
            OpenMBeanParameterInfo addWildcardChildName = null;
            if (entry.getValue().getElement().isWildcard()) {
                addWildcardChildName = new OpenMBeanParameterInfoSupport("name", "The name of the " + entry.getValue().getElement().getKey() + " to add.", SimpleType.STRING);
            }

            ops.add(getOperation(NameConverter.createValidAddOperationName(entry.getKey()), addWildcardChildName, entry.getValue().getOperationEntry()));
        }
    }

    private OpenMBeanOperationInfo getOperation(String name, OpenMBeanParameterInfo addWildcardChildName, OperationEntry entry) {
        ModelNode opNode = entry.getDescriptionProvider().getModelDescription(null);
        OpenMBeanParameterInfo[] params = getParameterInfos(entry.getOperationDefinition(), opNode);
        if (addWildcardChildName != null) {
            OpenMBeanParameterInfo[] newParams = new OpenMBeanParameterInfo[params.length + 1];
            newParams[0] = addWildcardChildName;
            System.arraycopy(params, 0, newParams, 1, params.length);
            params = newParams;
        }
        return new OpenMBeanOperationInfoSupport(
                name,
                getDescription(opNode),
                params,
                getReturnType(opNode),
                entry.getFlags().contains(Flag.READ_ONLY) ? MBeanOperationInfo.INFO : MBeanOperationInfo.UNKNOWN,
                createOperationDescriptor());
    }

    private OpenMBeanParameterInfo[] getParameterInfos(OperationDefinition opDef, ModelNode opNode) {
        if (!opNode.hasDefined(REQUEST_PROPERTIES)) {
            return EMPTY_PARAMETERS;
        }
        List<Property> propertyList = opNode.get(REQUEST_PROPERTIES).asPropertyList();
        List<OpenMBeanParameterInfo> params = new ArrayList<OpenMBeanParameterInfo>(propertyList.size());

        Map<String, AttributeDefinition> attributeDefinitions = new HashMap<>();
        AttributeDefinition[] attrs = opDef.getParameters();
        if (attrs != null) {
            for (AttributeDefinition attributeDefinition : attrs) {
                attributeDefinitions.put(attributeDefinition.getName(), attributeDefinition);
            }
        }

        for (Property prop : propertyList) {
            String name = prop.getName();
            ModelNode value = prop.getValue();
            AttributeDefinition attributeDefinition = attributeDefinitions.get(name);

            String paramName = NameConverter.convertToCamelCase(name);

            Map<String, Object> descriptions = new HashMap<String, Object>(4);

            boolean expressionsAllowed = value.hasDefined(EXPRESSIONS_ALLOWED) && value.get(EXPRESSIONS_ALLOWED).asBoolean();
            descriptions.put(DESC_EXPRESSIONS_ALLOWED, String.valueOf(expressionsAllowed));

            if (!expressionsAllowed) {
                Object defaultValue = getIfExists(attributeDefinition, value, DEFAULT);
                descriptions.put(DEFAULT_VALUE_FIELD, defaultValue);
                if (value.has(ALLOWED)) {
                    if (value.get(TYPE).asType()!=ModelType.LIST){
                        List<ModelNode> allowed = value.get(ALLOWED).asList();
                        descriptions.put(LEGAL_VALUES_FIELD, fromModelNodes(allowed));
                    }
                } else {
                    if (value.has(MIN)) {
                        Comparable minC = getIfExistsAsComparable(attributeDefinition, value, MIN);
                        if (minC instanceof Number) {
                            descriptions.put(MIN_VALUE_FIELD, minC);
                        }
                    }
                    if (value.has(MAX)) {
                        Comparable maxC = getIfExistsAsComparable(attributeDefinition, value, MAX);
                        if (maxC instanceof Number) {
                            descriptions.put(MAX_VALUE_FIELD, maxC);
                        }
                    }
                }
            }


            params.add(
                    new OpenMBeanParameterInfoSupport(
                            paramName,
                            getDescription(value),
                            converters.convertToMBeanType(attributeDefinition, value),
                            new ImmutableDescriptor(descriptions)));

        }
        return params.toArray(new OpenMBeanParameterInfo[params.size()]);
    }

    private Set<?> fromModelNodes(final List<ModelNode> nodes) {
        Set<Object> values = new HashSet<Object>(nodes.size());
        for (ModelNode node : nodes) {
            values.add(converters.getConverter(null, ModelType.STRING,null).fromModelNode(node));
        }
        return values;
    }

    private Object getIfExists(AttributeDefinition attributeDefinition, final ModelNode parentNode, final String name) {
        if (parentNode.has(name)) {
            ModelNode defaultNode = parentNode.get(name);
            return converters.fromModelNode(attributeDefinition, parentNode, defaultNode);
        } else {
            return null;
        }
    }

    private Comparable<?> getIfExistsAsComparable(AttributeDefinition attributeDefinition, final ModelNode parentNode, final String name) {
        if (parentNode.has(name)) {
            ModelNode defaultNode = parentNode.get(name);
            Object value = converters.fromModelNode(attributeDefinition, parentNode, defaultNode);
            if (value instanceof Comparable) {
                return (Comparable<?>) value;
            }
        }
        return null;
    }

    private OpenType<?> getReturnType(ModelNode opNode) {
        if (!opNode.hasDefined(REPLY_PROPERTIES)) {
            return SimpleType.VOID;
        }
        if (opNode.get(REPLY_PROPERTIES).asList().isEmpty()) {
            return SimpleType.VOID;
        }

        //TODO might have more than one REPLY_PROPERTIES?
        ModelNode reply = opNode.get(REPLY_PROPERTIES);
        return converters.convertToMBeanType(null, reply);
    }

    private MBeanNotificationInfo[] getNotifications() {
        List<MBeanNotificationInfo> notifications = new ArrayList<>();
        for (Map.Entry<String, NotificationEntry> entry : resourceRegistration.getNotificationDescriptions(PathAddress.EMPTY_ADDRESS, true).entrySet()) {
            ModelNode descriptionModel = entry.getValue().getDescriptionProvider().getModelDescription(null);
            String description = descriptionModel.get(DESCRIPTION).asString();
            String notificationType = entry.getKey();
            MBeanNotificationInfo info = null;
            if (notificationType.equals(ModelDescriptionConstants.ATTRIBUTE_VALUE_WRITTEN_NOTIFICATION)) {
                info = new MBeanNotificationInfo(new String[]{AttributeChangeNotification.ATTRIBUTE_CHANGE}, AttributeChangeNotification.class.getName(), description);
            } else if (notificationType.equals(ModelDescriptionConstants.RESOURCE_ADDED_NOTIFICATION)
                    || notificationType.equals(ModelDescriptionConstants.RESOURCE_REMOVED_NOTIFICATION)) {
                // do not expose these notifications as they are emitted by the JMImplementation:type=MBeanServerDelegate MBean.
            } else {
                info = new MBeanNotificationInfo(new String[]{notificationType}, Notification.class.getName(), description);
            }
            if (info != null) {
                notifications.add(info);
            }
        }
        return notifications.toArray(new MBeanNotificationInfo[notifications.size()]);
    }

    private Descriptor createMBeanDescriptor() {
        Map<String, String> descriptions = new HashMap<String, String>();
        addMBeanExpressionSupport(descriptions);
        return new ImmutableDescriptor(descriptions);
    }

    private Descriptor createAttributeDescriptor(ModelNode attribute) {
        Map<String, String> descriptions = new HashMap<String, String>();
        addMBeanExpressionSupport(descriptions);
        boolean allowExpressions = attribute.hasDefined(EXPRESSIONS_ALLOWED) && attribute.get(EXPRESSIONS_ALLOWED).asBoolean();
        descriptions.put(DESC_EXPRESSIONS_ALLOWED, Boolean.toString(allowExpressions));
        descriptions.put(DESC_EXPRESSIONS_ALLOWED_DESC, allowExpressions ?
                JmxLogger.ROOT_LOGGER.descriptorAttributeExpressionsAllowedTrue() : JmxLogger.ROOT_LOGGER.descriptorAttributeExpressionsAllowedFalse());
        return new ImmutableDescriptor(descriptions);
    }

    private Descriptor createOperationDescriptor() {
        Map<String, String> descriptions = new HashMap<String, String>();
        addMBeanExpressionSupport(descriptions);
        return new ImmutableDescriptor(descriptions);
    }

    private void addMBeanExpressionSupport(Map<String, String> descriptions) {
        if (legacy) {
            descriptions.put(DESC_MBEAN_EXPR, "true");
            descriptions.put(DESC_MBEAN_EXPR_DESCR, JmxLogger.ROOT_LOGGER.descriptorMBeanExpressionSupportFalse());
            if (configuredDomains.getExprDomain() != null) {
                ObjectName alternate = configuredDomains.getMirroredObjectName(name);
                descriptions.put(DESC_ALTERNATE_MBEAN, alternate.toString());
                descriptions.put(DESC_ALTERNATE_MBEAN_DESCR, JmxLogger.ROOT_LOGGER.descriptorAlternateMBeanExpressions(alternate));
            }
        } else {
            descriptions.put(DESC_MBEAN_EXPR, "false");
            descriptions.put(DESC_MBEAN_EXPR_DESCR, JmxLogger.ROOT_LOGGER.descriptorMBeanExpressionSupportTrue());
            if (configuredDomains.getLegacyDomain() != null) {
                ObjectName alternate = configuredDomains.getMirroredObjectName(name);
                descriptions.put(DESC_ALTERNATE_MBEAN, alternate.toString());
                descriptions.put(DESC_ALTERNATE_MBEAN_DESCR, JmxLogger.ROOT_LOGGER.descriptorAlternateMBeanLegacy(alternate));
            }
        }
    }
}
