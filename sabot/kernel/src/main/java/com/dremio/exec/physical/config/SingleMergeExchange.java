/*
 * Copyright (C) 2017-2019 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dremio.exec.physical.config;

import java.util.Collection;
import java.util.List;

import com.dremio.common.logical.data.Order.Ordering;
import com.dremio.exec.physical.EndpointAffinity;
import com.dremio.exec.physical.PhysicalOperatorSetupException;
import com.dremio.exec.physical.base.AbstractExchange;
import com.dremio.exec.physical.base.OpProps;
import com.dremio.exec.physical.base.PhysicalOperator;
import com.dremio.exec.physical.base.PhysicalOperatorUtil;
import com.dremio.exec.physical.base.Receiver;
import com.dremio.exec.physical.base.Sender;
import com.dremio.exec.planner.fragment.EndpointsIndex;
import com.dremio.exec.planner.fragment.ParallelizationInfo;
import com.dremio.exec.proto.CoordinationProtos.NodeEndpoint;
import com.dremio.exec.record.BatchSchema;
import com.dremio.options.OptionManager;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;

public class SingleMergeExchange extends AbstractExchange {
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SingleMergeExchange.class);

  private final List<Ordering> orderExpr;
  private final OptionManager optionManager;

  public SingleMergeExchange(
      OpProps props,
      OpProps senderProps,
      OpProps receiverProps,
      BatchSchema schema,
      PhysicalOperator child,
      List<Ordering> orderExpr,
      OptionManager optionManager) {
    super(props, senderProps, receiverProps, schema, child, optionManager);
    this.orderExpr = orderExpr;
    this.optionManager = optionManager;
  }

  @Override
  public ParallelizationInfo.WidthConstraint getReceiverParallelizationWidthConstraint() {
    return ParallelizationInfo.WidthConstraint.SINGLE;
  }

  @Override
  public Supplier<Collection<EndpointAffinity>> getReceiverEndpointAffinity(Supplier<Collection<NodeEndpoint>> senderFragmentEndpointsSupplier) {
    return () -> {
      Collection<NodeEndpoint> senderFragmentEndpoints = senderFragmentEndpointsSupplier.get();
      Preconditions.checkArgument(senderFragmentEndpoints != null && senderFragmentEndpoints.size() > 0,
        "Sender fragment endpoint list should not be empty");
      return getDefaultAffinityMap(senderFragmentEndpoints);
    };
  }

  @Override
  protected void setupReceivers(List<NodeEndpoint> receiverLocations)
      throws PhysicalOperatorSetupException {
    Preconditions.checkArgument(receiverLocations.size() == 1,
      "SingleMergeExchange only supports a single receiver endpoint.");

    super.setupReceivers(receiverLocations);
  }

  @Override
  public Sender getSender(int minorFragmentId, PhysicalOperator child, EndpointsIndex.Builder indexBuilder) {
    return new SingleSender(senderProps, schema, child, receiverMajorFragmentId,
      indexBuilder.addFragmentEndpoint(0, receiverLocations.iterator().next()));
  }

  @Override
  public Receiver getReceiver(int minorFragmentId, EndpointsIndex.Builder indexBuilder) {
    return new MergingReceiverPOP(receiverProps, schema, senderMajorFragmentId, PhysicalOperatorUtil.getIndexOrderedEndpoints(senderLocations, indexBuilder), false, orderExpr);
  }

  @Override
  protected PhysicalOperator getNewWithChild(PhysicalOperator child) {
    return new SingleMergeExchange(props, senderProps, receiverProps, schema, child, orderExpr, optionManager);
  }

  @JsonProperty("orderings")
  public List<Ordering> getOrderings() {
    return this.orderExpr;
  }

}
