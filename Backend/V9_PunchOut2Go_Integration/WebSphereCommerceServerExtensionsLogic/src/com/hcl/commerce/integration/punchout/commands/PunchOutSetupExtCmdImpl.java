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

import javax.persistence.NoResultException;

import com.ibm.commerce.command.CommandFactory;
import com.ibm.commerce.command.ControllerCommandImpl;
import com.ibm.commerce.datatype.TypedProperty;
import com.ibm.commerce.exception.ECApplicationException;
import com.ibm.commerce.exception.ECException;
import com.ibm.commerce.exception.ECSystemException;
import com.ibm.commerce.me.commands.AuthenticationHelperCmd;
import com.ibm.commerce.me.commands.PunchOutSetupCmd;
import com.ibm.commerce.me.common.ECMEMessage;
import com.ibm.commerce.me.common.ECMEOrderConstants;
import com.ibm.commerce.me.common.ECMEUserConstants;
import com.ibm.commerce.me.common.ErrorConstants;
import com.ibm.commerce.me.common.Status;
import com.ibm.commerce.me.datatype.CIData;
import com.ibm.commerce.me.datatype.CIDataImpl;
import com.ibm.commerce.me.datatype.SessionInfo;
import com.ibm.commerce.me.datatype.StoreHelper;
import com.ibm.commerce.me.objects.ProcurementBuyerProfileAccessBean;
import com.ibm.commerce.me.objects.ProcurementProtocolAccessBean;
import com.ibm.commerce.order.objects.OrderAccessBean;
import com.ibm.commerce.order.objects.OrderItemAccessBean;
import com.ibm.commerce.ras.ECMessage;
import com.ibm.commerce.ras.ECMessageHelper;
import com.ibm.commerce.ras.ECTrace;
import com.ibm.commerce.ras.ECTraceIdentifiers;
import com.ibm.commerce.server.ECConstants;
import com.ibm.commerce.user.objects.UserRegistryAccessBean;

/**
 * This is the implementation of PunchOutSetupCmd. It is used when the Session
 * Setup request is received from a procurement system for a requisitioning
 * user. It performs the authentication of the buyer organization and registers
 * the requisitioning user as a member of the buyer organization if not already
 * registered.
 */
public class PunchOutSetupExtCmdImpl extends ControllerCommandImpl implements PunchOutSetupCmd {
	private static final String COPYRIGHT = "(c) Copyright International Business Machines Corporation 1996,2008";

	// name of this class
	private static final String CLASS_NAME = "com.hcl.commerce.integration.punchout.commands.PunchOutSetupCmdImpl";

	// login mode - can be edit, inspect, create or display
	private short logonMode = -1;

	// userId
	private Long userId = null;

	// is the check parameters ok ?
	private boolean checkParametersOk = false;

	private Long buyerId;

	// the errorcode
	private int errorCode = ECMEUserConstants.NO_ERROR;

	/**
	 * CIData object to store logon properties.
	 */
	protected CIData ciData;

	private String reqEmailId;

	private ProcurementProtocolAccessBean protocolBean = null;
	private Integer protocolId = null;

	/**
	 * The task view used after successful command completion.
	 */
	private static final String VIEW_TASK = "PunchOutSetupOKView";

	/**
	 * The task view used when an error has occurred.
	 */
	private static final String ERROR_TASK = "PunchOutSetupErrorView";

	private int authType = -1;

	private String requisitionerId = null;
	private Long organizationUnitId = null;

	/**
	 * This method changes the status of a given order to <code>P</code> if it
	 * is <code>W</code>. If successful the status of all order items will be
	 * changed to <code>P</code>.
	 *
	 * @param order
	 *            ID
	 * @exception com.ibm.commerce.exception.ECException
	 *                Raised when an exception occurs while editing the shopping
	 *                cart.
	 */
	protected void changeOrderStatus(String orderId) throws ECException {
		final String strMethodName = "changeOrderStatus";
		ECTrace.entry(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);

		try {
			// change status in orders table

			OrderAccessBean orderBean = new OrderAccessBean();
			// find the order entry from the order ref number (orders table)
			orderBean.setInitKey_orderId(orderId);
			orderBean.instantiateEntity();

			setUsersId(orderBean.getMemberIdInEntityType());
			updateStatus(orderBean);

		} catch (NoResultException e) {

			ECTrace.trace(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName,
					"Exception occurred when editing shopping cart" + e);
			throw new ECSystemException(ECMessage._ERR_FINDER_EXCEPTION, getClass().getName(), strMethodName,
					ECMessageHelper.generateMsgParms(e.toString()), e, true);
		}
		ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);

	}

	/**
	 * Method updateStatus.
	 * 
	 * @param orderBean
	 * @exception com.ibm.commerce.exception.ECException
	 */
	private void updateStatus(OrderAccessBean orderBean) throws ECException {
		final String strMethod = "updateStatus";
		ECTrace.entry(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethod);
		try {
			String status = orderBean.getStatus();
			if (status.equalsIgnoreCase(ECMEOrderConstants.ORDER_SUBMITTED_TO_PROCUREMENT)) {
				orderBean.setStatus(ECMEOrderConstants.ORDER_PENDING);
				OrderItemAccessBean[] orderItems = orderBean.getOrderItems();
				String itemStatus = null;
				for (int i = 0; i < orderItems.length; i++) {
					OrderItemAccessBean anOrderItem = orderItems[i];
					itemStatus = anOrderItem.getStatus();
					if (itemStatus != null
							&& itemStatus.equalsIgnoreCase(ECMEOrderConstants.ORDER_SUBMITTED_TO_PROCUREMENT)) {
						anOrderItem.setStatus(ECMEOrderConstants.ORDER_PENDING);
					}
				}
			} else {
				ECTrace.trace(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethod,
						"The order status is invalid  status = " + status);
			}
		} catch (NoResultException ex) {
			throw new ECSystemException(ECMessage._ERR_FINDER_EXCEPTION, getClass().getName(), strMethod, ex);
		}
		ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethod);

	}

	/**
	 * This method gets the authentication type.
	 * 
	 * @return authType The authentication type.
	 */
	public int getAuthType() {
		return authType;
	}

	private Long getBuyerOrganizationId() {
		return buyerId;
	}

	/**
	 * This method gets the Organization unit ID
	 * 
	 * @return organizationUnitId The organization unit ID.
	 */
	public Long getOrganizationUnitId() {
		return organizationUnitId;
	}

	/**
	 * This method gets the protocol ID.
	 * 
	 * @return protocolId The protocol ID.
	 */
	public Integer getProtocolId() {
		return protocolId;
	}

	/**
	 * This method gets the requisitioner ID.
	 * 
	 * @return requisitionerId The requisitioner ID.
	 */
	public String getRequisitionerId() {
		return requisitionerId;
	}

	/**
	 * This method gets the user ID.
	 * 
	 * @return userId The user ID
	 */
	public Long getUsersId() {
		return userId;
	}

	private void handleError(String errorView, int anErrorCode) {
		final String strMethodName = "handleError";

		Status s = Status.getStatus(anErrorCode, getCommandContext().getLocale());

		ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName,
				ECMEUserConstants.STATUS_CODE + s.getCode());

		ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName,
				ECMEUserConstants.STATUS_TEXT + s.getText());

		ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName,
				ECMEUserConstants.STATUS_MESSAGE + s.getMessage());

		responseProperties.put(ECConstants.EC_VIEWTASKNAME, errorView);

		responseProperties.put(ECMEUserConstants.STATUS_CODE, "" + s.getCode());
		responseProperties.put(ECMEUserConstants.STATUS_TEXT, s.getText());
		responseProperties.put(ECMEUserConstants.STATUS_MESSAGE, s.getMessage());
		responseProperties.put(ECMEUserConstants.PROCUREMENT_ERROR_CODE, String.valueOf(anErrorCode));
	}

	/**
	 * This method initializes input data.
	 * 
	 * @see com.ibm.commerce.me.datatype#CIDataImpl
	 */
	protected void initializeInputData() {
		ciData = new CIDataImpl(getCommandContext());
	}

	/**
	 * This method checks if the user is a generic user or not.
	 *
	 * @return This method always returns true. A user is generic until they
	 *         logon, so always return true
	 */
	public boolean isGeneric() {
		return true;
	}

	/**
	 * This method checks whether the protocol currently used is single step or
	 * a two step protocol.
	 * 
	 * @return True if the protocol is two steps, false otherwise.
	 * 
	 */
	protected boolean isTwoStepProtocol() {
		final String strMethodName = "isTwoStepProtocol";

		ECTrace.entry(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);

		boolean res = true;

		try {
			String mode = protocolBean.getTwoStepMode();
			if (mode.equals("N")) {
				res = false;
			}
		} catch (Exception e) {
			if (ECTrace.traceEnabled(ECTraceIdentifiers.COMPONENT_USER)) {
				ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName,
						"Exception: " + e.toString() + " isTwoStepProtocol returns " + res);
			}
		}

		ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);

		return res;
	}

	/**
	 * The business logic for this controller command.
	 * <P>
	 * This method will do the following: 1. Gets <samp>sessionInfo</samp> from
	 * CIData 2. Stores the protocol table ID in the <samp>sessionInfo</samp> 3.
	 * Uses the Authentication Helper Command to Authenticate User 4. Sets the
	 * properties of the <samp>AuthenticationHelperCommand</samp> 5. If the user
	 * is authenticated, gets the procurement buyer profile properties 6.
	 * Invokes requisitioner registration command
	 * 
	 * @exception com.ibm.commerce.exception.ECException
	 *                Raised when failed to register requisitioner; or failed to
	 *                retrieve catalogId from
	 *                <samp>BuyerSupplierMappingBean</samp>.
	 */
	public void performExecute() throws ECException {
		final String strMethodName = "performExecute";
		ECTrace.entry(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);
		responseProperties = new TypedProperty();

		SessionInfo sessionInfo = ciData.getSessionInfo();
		if (sessionInfo == null) {
			ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName,
					"Session Info is null");
		}

		sessionInfo.setProcurementProtocolId(getProtocolId().toString());

		logonMode = sessionInfo.getLogonMode();
		ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName,
				"logonMode = " + logonMode);

		ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName,
				"Initial store id in the command context = " + commandContext.getStoreId());

		// Use the Authentication Helper Command to Authenticate User
		AuthenticationHelperCmd authHelpCmd = (AuthenticationHelperCmd) CommandFactory
				.createCommand(AuthenticationHelperCmd.NAME, commandContext.getStoreId());

		// Set the properties of the AuthenticationHelperCommand
		authHelpCmd.setBuyerCredentials(ciData.getBuyerCredentials());
		authHelpCmd.setSupplierCredentials(ciData.getSupplierCredentials());
		authHelpCmd.setMarketPlaceBuyerCredentials(ciData.getMarketPlaceCredentials());
		authHelpCmd.setSessionInfo(ciData.getSessionInfo());
		authHelpCmd.setCommandContext(getCommandContext());
		authHelpCmd.setAuthType(getAuthType());
		authHelpCmd.setProtocolId(protocolId);

		boolean failure = false;

		try {
			authHelpCmd.execute();
		} catch (ECException eCmd) {
			failure = true;
		}

		// Check to see whether the user is authenticated or not
		if (failure || (!authHelpCmd.isValidCredentials())) {

			errorCode = authHelpCmd.getErrorCode();
			ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName,
					"authHelpCmd.getErrorCode() :" + errorCode);

			handleError(ERROR_TASK, errorCode);

		} else {

			// authnetication is successful. Now get the procurement buyer
			// profile
			// properties
			// such as requisitionerId
			buyerId = new Long(authHelpCmd.getBuyerId());
			setBuyerRequestProperties();
			sessionInfo.setReqId(getRequisitionerId());

			String customViewTask = StoreHelper.getResponseViewName(String.valueOf(protocolId),
					Long.toString(authHelpCmd.getBuyerId()), "PunchOutSetup");
			if (customViewTask == null) {
				customViewTask = VIEW_TASK;
			}
			String customErrorTask = StoreHelper.getResponseErrorView(String.valueOf(protocolId),
					Long.toString(authHelpCmd.getBuyerId()), "PunchOutSetup");
			if (customErrorTask == null) {
				customErrorTask = ERROR_TASK;
			}

			// call the requisitioner registration command.
			try {
				RegisterRequisitionerCmd cmd = (RegisterRequisitionerCmd) CommandFactory.createCommand(
						"com.ibm.commerce.me.commands.RegisterRequisitionerCmd", commandContext.getStoreId());
				cmd.setSessionInfo(sessionInfo);
				cmd.setEmail(getReqEmailId());
				cmd.setBuyerId(authHelpCmd.getBuyerId());
				cmd.setSupplierId(authHelpCmd.getSupplierId());
				cmd.setCommandContext(getCommandContext());
				cmd.execute();

				userId = cmd.getUsersId();
				if (userId != null) {
					getCommandContext().setUserId(userId);
				}
			} catch (ECApplicationException expCmd) {
				ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName,
						"Application Exception from Registering Requisitioner" + expCmd.toString());

				throw expCmd;
			} catch (ECSystemException expCmd) {
				Status s = Status.getStatus(ErrorConstants.REGISTER_REQUISITIONER_FAILED,
						getCommandContext().getLocale());
				responseProperties.put(ECMEUserConstants.STATUS_CODE, "" + s.getCode());
				responseProperties.put(ECMEUserConstants.STATUS_TEXT, s.getText());
				responseProperties.put(ECMEUserConstants.STATUS_MESSAGE, s.getMessage());
				responseProperties.put(ECMEUserConstants.PROCUREMENT_ERROR_CODE,
						String.valueOf(ErrorConstants.REGISTER_REQUISITIONER_FAILED));

				throw new ECApplicationException(

						ECMEMessage._ERR_PROCUREMENT_RESP_REGISTER_REQUISITIONER_FAILED, getClass().getName(),
						strMethodName, customErrorTask, responseProperties);
			}

			// if the logon mode is edit then change
			// the order status from 'W' to 'P'
			if (logonMode == ECMEUserConstants.EDIT_MODE) {
				changeOrderStatus(Long.toString(sessionInfo.getShoppingCartId()));
			}
		}

		ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);
	}

	/**
	 * This method sets the authentication type.
	 * 
	 * @param anAuthType
	 *            The authentication type
	 */
	public void setAuthType(int anAuthType) {
		authType = anAuthType;
	}

	/**
	 * This method sets the buyer request properties.
	 */
	protected void setBuyerRequestProperties() {
		final String strMethodName = "setBuyerRequestProperties";
		ECTrace.entry(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);
		if ((getProtocolId() == null) || (getBuyerOrganizationId() == null)) {
			return;
		}

		String orgUnitName = null;
		String reqId = null;
		try {

			ProcurementBuyerProfileAccessBean aBean = new ProcurementBuyerProfileAccessBean();
			aBean.setInitKey_protocolId(getProtocolId().toString());
			aBean.setInitKey_organizationId(getBuyerOrganizationId().toString());
			aBean.instantiateEntity();

			reqId = requestProperties.getString(aBean.getReqIdParamName(), null);

			setRequisitionerId(reqId);
			ciData.getSessionInfo().setReqId(reqId);

			orgUnitName = requestProperties.getString(aBean.getOrganizationUnitParamName(), null);

		} catch (Exception e) {
			if (ECTrace.traceEnabled(ECTraceIdentifiers.COMPONENT_USER)) {
				ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName, "Exception: "
						+ e.toString() + " reqId: " + reqId + " ciData: " + ciData + " orgUnitName: " + orgUnitName);
			}
		}

		ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);
	}

	/**
	 * This method sets the organization unit ID.
	 * 
	 * @param orgUnit
	 *            The organization unit ID.
	 */
	public void setOrganizationUnitId(Long orgUnit) {
		organizationUnitId = orgUnit;
	}

	/**
	 * The Web controller calls the setRequestProperties method before invoking
	 * the execute method in this command. It is the responsibility of the
	 * implementer of the ControllerCommand to extract the required input
	 * parameters from the request properties and perform parameter checking.
	 * 
	 * @param requestProperties
	 *            com.ibm.commerce.datatype.TypedProperty
	 * @exception com.ibm.commerce.exception.ECException.
	 */
	public void setRequestProperties(TypedProperty p) throws ECException {
		final String strMethodName = "setRequestProperties";
		ECTrace.entry(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);

		requestProperties = p;

		setReqEmailId(p.getString("reqEmailId", null));

		initializeInputData();

		// now set the properties in the ciData object
		ciData.setLogonData(p);

		ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName,
				"reqProperties =  " + p.toString());

		ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);
	}

	/**
	 * This method sets the requisitioner ID.
	 * 
	 * @param newRequisitionerId
	 *            The requisitioner ID.
	 */
	public void setRequisitionerId(String newRequisitionerId) {
		this.requisitionerId = newRequisitionerId;

		ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), "setrequisitionerId",
				"requisitionerId = " + this.requisitionerId);
	}

	/**
	 * This method sets the User ID of the owner of the order.
	 * 
	 * @param: newUserId
	 *             The user ID.
	 */
	public void setUsersId(Long newUserId) {
		this.userId = newUserId;
	}

	/**
	 * Switches the current user identity to the identity associated with the
	 * login ID supplied to this logon command.
	 *
	 * @exception ECException
	 */
	protected void updateCmdContext() throws ECException {
		final String strMethodName = "updateCmdContext";
		ECTrace.entry(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);

		Long lNewUserRefNum = null;
		UserRegistryAccessBean abNewUserReg = null;

		try {
			abNewUserReg = new UserRegistryAccessBean();
			abNewUserReg = abNewUserReg.findByUserLogonId(ciData.getSessionInfo().getReqId());

			lNewUserRefNum = new Long(abNewUserReg.getUserId());

		} catch (Exception e) {
			if (ECTrace.traceEnabled(ECTraceIdentifiers.COMPONENT_USER)) {
				ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName,
						"Exception: " + e.toString() + " abNewUserReg: " + abNewUserReg);
			}
		}

		commandContext.setUserId(lNewUserRefNum);

		ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);
	}

	/**
	 * This method validates the required parameters for this controller
	 * command. The required parameters are <samp>protocolName</samp>,
	 * <samp>protocolVersion</samp>, <samp>protocolType</samp>.
	 * <p>
	 * 
	 * @exception ECException.
	 */
	public void validateParameters() throws ECException {
		final String strMethodName = "validateParameters";
		ECTrace.entry(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);

		checkParametersOk = false;

		try {
			ProcurementProtocolAccessBean aBean = new ProcurementProtocolAccessBean();

			protocolBean = aBean.findByProtocolNameAndVersion(ciData.getProtocolName(), ciData.getProtocolVersion());
			protocolId = protocolBean.getProtocolIdInEntityType();
			setAuthType(protocolBean.getAuthenticationTypeInEntityType().intValue());
		} catch (NoResultException e) {
			TypedProperty exceptionProperties = new TypedProperty();

			Status s = Status.getStatus(ErrorConstants.INVALID_PROCUREMENT_PROTOCOL, getCommandContext().getLocale());
			exceptionProperties.put(ECMEUserConstants.STATUS_CODE, "" + s.getCode());
			exceptionProperties.put(ECMEUserConstants.STATUS_TEXT, s.getText());
			exceptionProperties.put(ECMEUserConstants.STATUS_MESSAGE, s.getMessage());
			exceptionProperties.put(ECMEUserConstants.PROCUREMENT_ERROR_CODE,
					String.valueOf(ErrorConstants.INVALID_PROCUREMENT_PROTOCOL));

			throw new ECApplicationException(ECMEMessage._ERR_PROCUREMENT_INVALID_PROTOCOL, getClass().getName(),
					strMethodName, ERROR_TASK, exceptionProperties, true);

		}

		checkParametersOk = true;
		ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);
	}

	/**
	 * This method gets the CIData object.
	 * 
	 * @return The CIData object.
	 * @see com.ibm.commerce.me.datatype#CIDataImpl
	 */
	public CIData getCIData() {
		return this.ciData;
	}

	/**
	 * This method sets the CIData object to store the logon properties.
	 * 
	 * @param: newCIData
	 *             The CIData object.
	 * @see com.ibm.commerce.me.datatype#CIDataImpl
	 */
	public void setCIData(CIData newCIData) {
		this.ciData = newCIData;
	}

	/**
	 * This method gets the Requstion User Email Id.
	 * 
	 * @return reqEmailId The Requstion User Email Id.
	 */
	public String getReqEmailId() {
		return this.reqEmailId;
	}

	/**
	 * This method sets the Requstion User Email Id.
	 * 
	 * @param: reqEmailId
	 *             The Requstion User Email Id.
	 */
	public void setReqEmailId(String reqEmailId) {
		this.reqEmailId = reqEmailId;
	}
}
