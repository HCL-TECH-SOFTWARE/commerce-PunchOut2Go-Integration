/**
	*==================================================
	Copyright [2021] [HCL Technologies]

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0


	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
	*==================================================
**/
package com.hcl.commerce.integration.punchout.commands;

import java.util.Enumeration;

import javax.persistence.NoResultException;

import com.ibm.commerce.accesscontrol.AccessVector;
import com.ibm.commerce.command.ControllerCommandImpl;
import com.ibm.commerce.datatype.TypedProperty;
import com.ibm.commerce.exception.ECApplicationException;
import com.ibm.commerce.exception.ECException;
import com.ibm.commerce.exception.ECSystemException;
import com.ibm.commerce.me.commands.SubmitShoppingCartCmd;
import com.ibm.commerce.me.common.ECMEOrderConstants;
import com.ibm.commerce.me.common.ECMEUserConstants;
import com.ibm.commerce.order.objects.OrderAccessBean;
import com.ibm.commerce.order.objects.OrderItemAccessBean;
import com.ibm.commerce.ras.ECMessage;
import com.ibm.commerce.ras.ECMessageHelper;
import com.ibm.commerce.ras.ECTrace;
import com.ibm.commerce.ras.ECTraceIdentifiers;
import com.ibm.commerce.server.ECConstants;

/**
 * This is the implementation of the <code>SubmitShoppingCart</code> command.
 * The <code>SubmitShoppingCart</code> command is invoked when the procurement
 * buyer submits or checks out their order during catalog browsing. This command
 * changes the status of the order and order items to W (awaiting approval) from
 * P (pending). Together with the <code>SendShoppingCart</code> command, the
 * <code>SubmitShoppingCart</code> command alters the shopping flow to send the
 * procurement buyer order to the procurement system.
 */
public class SubmitShoppingCartExtCmdImpl extends ControllerCommandImpl implements SubmitShoppingCartCmd {
	/**
	 * Copyright statement.
	 */
	public static final String COPYRIGHT = "(c) Copyright International Business Machines Corporation 1996,2008";
	// corresponds to orders_id of orders table
	private Long orders_Id = null;

	// is the check parameters ok ?
	private boolean checkParametersOk = false;

	/**
	 * The error task view used when an error has occurred.
	 */
	protected static final String ERROR_TASK = ECMEUserConstants.SUBMIT_SHOPPINGCART_ERROR_VIEW;
	/**
	 * The task view used after successful command completion.
	 */
	protected static final String VIEW_TASK = ECMEUserConstants.SUBMIT_SHOPPINGCART_OK_VIEW;

	/**
	 * This method gets the error task name.
	 * 
	 * @return <samp>errorTask</samp> The error task name
	 */
	public String getErrorTask() {
		return ERROR_TASK;
	}

	/**
	 * This method gets the order ID.
	 * 
	 * @return <code>orders_Id</code> The order ID.
	 */
	public Long getOrderId() {
		return orders_Id;
	}

	/**
	 * This method gets an <samp>AccessVector</samp> that contain
	 * <samp>OrderAccessBeans</samp>.
	 * 
	 * @return com.ibm.commerce.accesscontrol.AccessVector
	 * @throws ECException
	 */
	public AccessVector getResources() throws ECException {

		AccessVector resourceList = new AccessVector();
		OrderAccessBean orderAB = new OrderAccessBean();
		orderAB.setInitKey_orderId(getOrderId().toString());
		resourceList.addElement(orderAB);
		return resourceList;
	}

	/**
	 * This method gets the view task name.
	 * 
	 * @return viewTask The view task name
	 */
	public String getViewTask() {
		return VIEW_TASK;
	}

	/**
	 * This method changes the status order in the orders and order items table
	 * from status pending (P) to waiting approval (W). This occurs when
	 * submitting the shopping cart or order to the Procurement System for
	 * approval.
	 * 
	 * @exception CommandException.
	 */
	public void performExecute() throws ECException {
		final String strMethodName = "performExecute";

		ECTrace.entry(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);

		ECTrace.trace(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName,
				"orders_Id=" + getOrderId());
		try {

			// change status in orders table
			OrderAccessBean orderBean = new OrderAccessBean();

			// find the order entry from the order ref number (orders table)
			orderBean.setInitKey_orderId(getOrderId().toString());
			orderBean.instantiateEntity();

			updateStatus(orderBean);

		} catch (NoResultException e) {
			throw new ECSystemException(ECMessage._ERR_FINDER_EXCEPTION, getClass().getName(), strMethodName,
					ECMessageHelper.generateMsgParms(e.toString()), e);
		}

		responseProperties = new TypedProperty();
		responseProperties.put(ECConstants.EC_VIEWTASKNAME, getViewTask());
		responseProperties.put(ECMEUserConstants.ORDERS_ID, getOrderId());

		ECTrace.exit(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);

	}

	/**
	 * Method updateStatus.
	 * 
	 * @param orderBean
	 * @exception ECException
	 */
	private void updateStatus(OrderAccessBean orderBean) throws ECException {
		final String strMethod = "updateStatus";
		ECTrace.entry(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethod);
		try {
			String status_orders = orderBean.getStatus();
			if (status_orders.equalsIgnoreCase(ECMEOrderConstants.ORDER_PENDING)) {
				orderBean.setStatus(ECMEOrderConstants.ORDER_SUBMITTED_TO_PROCUREMENT);
			} else {
				ECTrace.trace(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethod,
						"Invalid order status [status = " + status_orders + "]");
				throw new ECApplicationException(ECMessage._ERR_GENERIC, getClass().getName(), strMethod,
						getErrorTask(), true);

			}
			OrderItemAccessBean orderItemBean = new OrderItemAccessBean();

			Enumeration e = orderItemBean.findByOrder(getOrderId());

			while (e.hasMoreElements()) {
				OrderItemAccessBean b = (OrderItemAccessBean) e.nextElement();
				String status_orderitems = b.getStatus();

				if (status_orderitems.equalsIgnoreCase(ECMEOrderConstants.ORDER_PENDING)) {
					b.setStatus(ECMEOrderConstants.ORDER_SUBMITTED_TO_PROCUREMENT);
				} else {
					ECTrace.trace(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethod,
							"Invalid order item status [status = " + status_orderitems + "]");
					throw new ECApplicationException(ECMessage._ERR_GENERIC, getClass().getName(), strMethod,
							getErrorTask(), true);

				}
			}
		} catch (NoResultException ex) {
			throw new ECSystemException(ECMessage._ERR_FINDER_EXCEPTION, getClass().getName(), strMethod, ex);
		}
		ECTrace.exit(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethod);
	}

	/**
	 * This method sets the order ID.
	 * 
	 * @param <code>orderId</code>
	 *            The order ID
	 */
	public void setOrderId(Long orderId) {
		orders_Id = orderId;
	}

	/**
	 * The Web controller calls the <samp>setRequestProperties</samp> method
	 * before invoking the execute method in this command. It is the
	 * responsibility of the implementer of the <samp>ControllerCommand</samp>
	 * to extract the required input parameters from the request properties and
	 * perform parameter checking. Three mandatory parameters should be set
	 * before calling this command:
	 * 
	 * <pre>
	 *  1. orderRefNumber
	 *      2. userID
	 *      3. password
	 * </pre>
	 * 
	 * @param requestProperties
	 *            com.ibm.commerce.datatype.TypedProperty
	 * @return void
	 * @exception com.ibm.commerce.exception.ECException.
	 */

	public void setRequestProperties(TypedProperty p) throws ECException {
		final String strMethodName = "setRequestProperties";
		ECTrace.entry(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);

		requestProperties = p;

		// now set the individual properties.
		orders_Id = p.getLong(ECMEUserConstants.ORDERS_ID, null);

		ECTrace.exit(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);

	}

	/**
	 * This method checks for the existence of the order ID. This method will
	 * not determine if the order ID is correct or incorrect.
	 * <p>
	 * 
	 * @exception ECException
	 *                Raised when the order ID is null.
	 */

	public void validateParameters() throws ECException {
		final String strMethodName = "validateParameters";
		ECTrace.entry(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);

		if (getOrderId() == null) {
			checkParametersOk = false;
			throw new ECApplicationException(ECMessage._ERR_ORDER_NOT_FOUND, getClass().getName(), strMethodName,
					ECMessageHelper.generateMsgParms(getOrderId().toString(), ECConstants.EC_ORDER_RN), getErrorTask(),
					true);
		}

		checkParametersOk = true;

		ECTrace.exit(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);
	}
}
