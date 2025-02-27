/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.global;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_CONTROL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.CHILD_TYPE;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.INCLUDE_SINGLETONS;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.OperationStepHandler} querying the children names of a given "child-type".
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ReadChildrenNamesHandler implements OperationStepHandler {

    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(READ_CHILDREN_NAMES_OPERATION, ControllerResolver.getResolver("global"))
            .setParameters(CHILD_TYPE, INCLUDE_SINGLETONS)
            .setReadOnly()
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.STRING)
            .build();

    static final OperationStepHandler INSTANCE = new ReadChildrenNamesHandler();

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final PathAddress address = context.getCurrentAddress();
        final String childType = CHILD_TYPE.resolveModelAttribute(context, operation).asString();
        final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS, false);
        ImmutableManagementResourceRegistration registry = context.getResourceRegistration();
        Map<String, Set<String>> childAddresses = GlobalOperationHandlers.getChildAddresses(context, address, registry, resource, childType);
        Set<String> childNames = childAddresses.get(childType);
        if (childNames == null) {
            throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.unknownChildType(childType));
        }
        final boolean singletons = INCLUDE_SINGLETONS.resolveModelAttribute(context, operation).asBoolean(false);
        if (singletons && isSingletonResource(registry, childType) && !registry.isRuntimeOnly()) {
            Set<PathElement> childTypes = registry.getChildAddresses(PathAddress.EMPTY_ADDRESS);
            for (PathElement child : childTypes) {
                if (childType.equals(child.getKey())) {
                    if(!isEmptyRuntimeResource(context, registry, PathAddress.pathAddress(child))) {
                        childNames.add(child.getValue());
                    }
                }
            }
        }
        // Sort the result
        childNames = new TreeSet<String>(childNames);
        ModelNode result = context.getResult();
        result.setEmptyList();
        PathAddress childAddress = address.append(PathElement.pathElement(childType));
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, childAddress);
        op.get(OPERATION_HEADERS).set(operation.get(OPERATION_HEADERS));
        ModelNode opAddr = op.get(OP_ADDR);
        ModelNode childProperty = opAddr.require(address.size());
        Set<Action.ActionEffect> actionEffects = EnumSet.of(Action.ActionEffect.ADDRESS);
        FilteredData fd = null;
        for (String childName : childNames) {
            childProperty.set(childType, new ModelNode(childName));
            if (context.authorize(op, actionEffects).getDecision() == AuthorizationResult.Decision.PERMIT) {
                result.add(childName);
            } else {
                if (fd == null) {
                    fd = new FilteredData(address);
                }
                fd.addAccessRestrictedResource(childAddress);
            }
        }

        if (fd != null) {
            context.getResponseHeaders().get(ACCESS_CONTROL).set(fd.toModelNode());
        }
    }

    private boolean isSingletonResource(final ImmutableManagementResourceRegistration registry, final String key) {
        return registry.getSubModel(PathAddress.pathAddress(PathElement.pathElement(key))) == null;
    }

    private boolean isEmptyRuntimeResource(final OperationContext context, final ImmutableManagementResourceRegistration registry, final PathAddress child) {
        if(registry.getSubModel(child).isRuntimeOnly()) {
            try {
                context.readResource(child);
            } catch(Resource.NoSuchResourceException ex) {
                return true;
            }
        }
        return false;
    }
}
