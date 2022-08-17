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

import java.sql.Timestamp;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.persistence.NoResultException;

import com.ibm.commerce.beans.DataBeanManager;
import com.ibm.commerce.command.CommandContext;
import com.ibm.commerce.command.CommandFactory;
import com.ibm.commerce.command.ControllerCommandImpl;
import com.ibm.commerce.common.objects.StoreAccessBean;
import com.ibm.commerce.contract.util.ECContractCmdConstants;
import com.ibm.commerce.datatype.TypedProperty;
import com.ibm.commerce.edp.commands.PIAddCmd;
import com.ibm.commerce.exception.ECApplicationException;
import com.ibm.commerce.exception.ECException;
import com.ibm.commerce.exception.ECSystemException;
import com.ibm.commerce.me.commands.AuthenticationHelperCmd;
import com.ibm.commerce.me.commands.BatchOrderRequestCmd;
import com.ibm.commerce.me.commands.CheckBatchOrderRequestCmd;
import com.ibm.commerce.me.commands.CreateShippingBillingAddressCmd;
import com.ibm.commerce.me.common.ECMEMessage;
import com.ibm.commerce.me.common.ECMEOrderConstants;
import com.ibm.commerce.me.common.ECMEUserConstants;
import com.ibm.commerce.me.common.ErrorConstants;
import com.ibm.commerce.me.common.Status;
import com.ibm.commerce.me.common.XMLConstants;
import com.ibm.commerce.me.datatype.Address;
import com.ibm.commerce.me.datatype.CIData;
import com.ibm.commerce.me.datatype.CIDataImpl;
import com.ibm.commerce.me.datatype.PaymentInfo;
import com.ibm.commerce.me.datatype.PurchaseOrderHeader;
import com.ibm.commerce.me.datatype.PurchaseOrderItem;
import com.ibm.commerce.me.datatype.SessionInfo;
import com.ibm.commerce.me.datatype.StoreHelper;
import com.ibm.commerce.me.objects.BuyerSupplierMappingAccessBean;
import com.ibm.commerce.me.objects.ProcurementBuyerProfileAccessBean;
import com.ibm.commerce.me.objects.ProcurementProtocolAccessBean;
import com.ibm.commerce.order.commands.PrepareProcurementOrderCmd;
import com.ibm.commerce.order.commands.ProcessOrderCmd;
import com.ibm.commerce.order.objects.OrderAccessBean;
import com.ibm.commerce.order.objects.OrderItemAccessBean;
import com.ibm.commerce.order.objects.OrderItemMessagingExtensionAccessBean;
import com.ibm.commerce.order.objects.OrderMessagingExtensionAccessBean;
import com.ibm.commerce.order.utils.OrderConstants;
import com.ibm.commerce.orderitems.commands.OrderItemAddCmd;
import com.ibm.commerce.orderitems.commands.OrderItemMoveCmd;
import com.ibm.commerce.payment.beans.PaymentTCInfo;
import com.ibm.commerce.payment.beans.UsablePaymentTCListDataBean;
import com.ibm.commerce.persistence.JpaEntityAccessBeanCacheUtil;
import com.ibm.commerce.ras.ECMessage;
import com.ibm.commerce.ras.ECMessageHelper;
import com.ibm.commerce.ras.ECTrace;
import com.ibm.commerce.ras.ECTraceIdentifiers;
import com.ibm.commerce.server.ECConstants;
import com.ibm.commerce.server.WcsApp;
import com.ibm.commerce.user.objects.BusinessProfileAccessBean;
import com.ibm.commerce.user.objects.OrganizationAccessBean;

/**
 * This controller command will receive a PurchaseOrderRequest from a buyer
 * organization using procurement system and creates an order.
 * 
 */
public class BatchOrderRequestExtCmdImpl extends ControllerCommandImpl implements BatchOrderRequestCmd {
	private static final String COPYRIGHT = "(c) Copyright International Business Machines Corporation 1996,2008";
	// name of this class
	private static final String CLASS_NAME = "com.ibm.commerce.me.commands.BatchOrderRequestExtCmdImpl";
	// is the check parameters ok ?
	private boolean checkParametersOk = false;

	// is the credentials valid ?
	private boolean validCredentials = false;

	// Error Code
	private int errorCode = XMLConstants.NO_ERROR;

	/**
	 * the Buyer Id
	 */
	protected long buyerId = -1L;

	/**
	 * the Supplier Id
	 */
	protected long supplierId = -1L;

	/**
	 * the Store Id
	 */
	protected Integer storeId = null;

	/**
	 * the user Id
	 */
	private Long usersId = null;

	/**
	 * the order Id
	 */
	protected Long orderId = null;

	/**
	 * the addressId
	 */
	protected Long btAddressId = null;

	/**
	 * the ship to address Id
	 */
	protected Long stAddressId = null;

	/**
	 * the CIData object
	 */
	protected CIData ciData = null;

	/**
	 * the <code>ProcurementProtocolAccessBean</code> object
	 */
	protected ProcurementProtocolAccessBean protocolBean = null;
	/**
	 * the protocol Id
	 */
	protected Integer protocolId = null;
	/**
	 * the authentication type
	 */
	protected Integer authType = null;
	/**
	 * the contract Id
	 */
	protected Long contractId = null;
	/**
	 * the catalog Id
	 */
	protected Long catalogId = null;
	/**
	 * the member group Id
	 */
	protected Long memberGroupId = null;
	/**
	 * the old order Id
	 */
	protected Long oldOrderId = null;
	/**
	 * the requisitioner Id
	 */
	protected String requisitionerId = null;
	/**
	 * the organization unit Id
	 */
	protected Long organizationUnitId = null;

	/**
	 * name of the response view
	 */
	private String viewTask = "PurchaseOrderView";
	/**
	 * name of the error view
	 */
	private String errorTask = "PurchaseOrderErrorView";
	/**
	 * indicate whether order is solicited, default to false
	 */
	protected boolean solicitedOrder = false;
	private Hashtable hshDupAddress = null;
	/**
	 * the vector containing the old quotes
	 */
	protected Vector oldQuotes = null;

	// Requisitioner id of the user who solicited the orders during
	// punch out setup and who submitted the shopping cart.
	private String orgReqId = null;

	/**
	 * check whether this is a solicited order or not based on shoppingCartId.
	 * If solicited, change the shopping cart status.
	 * 
	 * @exception ECException
	 */
	protected void changeOrderStatus() throws ECException {
		// check whether this is a solicited order or not
		// if solicited, change the shopping cart status.
		oldQuotes = new Vector();
		boolean first = true;

		if (ciData.getPOItems() != null) {
			for (int i = 0; i < ciData.getPOItems().size(); i++) {
				PurchaseOrderItem item = (PurchaseOrderItem) ciData.getPOItems().elementAt(i);
				if (item.getShoppingCartId() != -1L) {
					setSolicitedOrder(true);
					Long quoteId = new Long(item.getShoppingCartId());
					if (first) {
						retrieveUserInfo(quoteId);
						first = false;
					}
					if (!oldQuotes.contains(quoteId)) {
						oldQuotes.addElement(quoteId);
					}
				}
			}
		}

	}

	/**
	 * 
	 * Calls CheckBatchOrderRequestCmd to check whether the requested order is
	 * represents a duplicated order. ECException can be thrown during the
	 * command creation or execution.
	 *
	 * @return int error code
	 * @exception ECException
	 *                Raised by CheckBatchOrderRequestCmd
	 */
	protected int checkDuplicateOrderRequest() throws ECException {

		CheckBatchOrderRequestCmd checkPO = (CheckBatchOrderRequestCmd) CommandFactory
				.createCommand(CheckBatchOrderRequestCmd.NAME, commandContext.getStoreId());
		checkPO.setCommandContext(getCommandContext());
		checkPO.setMessageId(ciData.getPOHeader().getMessageId());
		checkPO.execute();
		return checkPO.getErrorCode();

	}

	/**
	 * Check the user registration. If the user is registered update the
	 * requisitions if necessary else register the user and get the users Id.
	 * The update and registration are done by invoking RegisterRequistionerCmd.
	 * ECException can be thrown when error occurs during the command creation
	 * and execution stages. The new user id is retrieved from the command and
	 * update the local variable usersId and the usersId in the current command
	 * context.
	 * 
	 * @exception ECException
	 *                Raised when error occurs during the command creation and
	 *                execution stages
	 */
	protected void checkRegistration() throws ECException {
		String strMethodName = "checkRegistration";
		ECTrace.entry(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);

		// Requisitioner Registration Added 05/14/01 by GRN
		// call the requisitioner registration command.
		try {
			SessionInfo sessionInfo = ciData.getSessionInfo();
			sessionInfo.setProcurementProtocolId(getProtocolId().toString());
			RegisterRequisitionerCmd regReqCmd = (RegisterRequisitionerCmd) CommandFactory
					.createCommand("com.ibm.commerce.me.commands.RegisterRequisitionerCmd", getStoreId());
			regReqCmd.setSessionInfo(sessionInfo);
			regReqCmd.setBuyerId(buyerId);
			regReqCmd.setSupplierId(supplierId);
			regReqCmd.setCommandContext(getCommandContext());
			regReqCmd.execute();

			// get the primary key value for the user id
			usersId = regReqCmd.getUsersId();
			getCommandContext().setUserId(usersId);
		} catch (ECApplicationException expCmd) {
			ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName,
					"Application Exception from Registering Requisitioner" + expCmd.toString());
			throw expCmd;
		} catch (ECSystemException expCmd) {
			ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName,
					"Exception from Registering Requisitioner" + expCmd.toString());
			throw new ECApplicationException(ECMEMessage._ERR_PROCUREMENT_RESP_REGISTER_REQUISITIONER_FAILED,
					getClass().getName(), strMethodName, errorTask);

		}

		ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);
	}

	/**
	 * Creates an order extension record. This includes migrating information
	 * from the purchase order header, eg. Order ID, Message ID, Comments.
	 * ECException is thrown when the <code>EJB</code> fails.
	 * 
	 * @param orderRn
	 *            Order reference number (key for the order extension record)
	 * @exception com.ibm.commerce.exception.ECException
	 *                Raised when the <code>EJB</code> encounters any problems
	 */
	protected void createOrderMessagingExtensionRecord(Long orderRn) throws ECException {
		String strMethodName = "createOrderMessagingExtensionRecord";

		ECTrace.entry(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);

		ECTrace.trace(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName,
				"Order Refnum = " + orderRn);

		PurchaseOrderHeader poHeader = ciData.getPOHeader();

		try {
			OrderMessagingExtensionAccessBean ordMeBean = new OrderMessagingExtensionAccessBean(orderRn);

			ordMeBean.setBuyerOrderId(poHeader.getOrderId());
			ordMeBean.setPayloadId(poHeader.getMessageId());

			// split the comment into two columns if larger that max lenght
			String s = poHeader.getComment();
			if (s != null && s.length() > ECMEOrderConstants.COMMENT_LENGTH) {
				String s1 = s.substring(0, ECMEOrderConstants.COMMENT_LENGTH);
				String s2 = s.substring(ECMEOrderConstants.COMMENT_LENGTH + 1);
				ordMeBean.setComments1(s1);
				ordMeBean.setComments2(s2);
			} else {
				ordMeBean.setComments1(s);
			}

			ordMeBean.setOrderType(ECMEOrderConstants.ORDER_TYPE_STAND_ALONE);
			if (poHeader.getOrderDate() != null) {
				ordMeBean.setRequestedTime(new Timestamp(poHeader.getOrderDate().getTime()));
			}

		} catch (Exception e) {
			this.errorCode = ErrorConstants.ORDER_CREATE_FAILED;
		}
		ECTrace.exit(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);

	}

	/**
	 * Delete the old order item from order with oldOrderRn as order id by
	 * calling the OrderItemMoveCmd
	 * 
	 * @param oldOrderRn
	 *            Old order reference number
	 * @exception ECException
	 *                Raised by OrderItemMoveCmd
	 */
	protected void deleteOldOrder(Long oldOrderRn) throws ECException {

		final String strMethodName = "deleteOldOrder";

		ECTrace.entry(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);

		ECTrace.trace(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName,
				"attempting to delete old older = " + oldOrderRn);

		try {

			// change status in orders table
			OrderAccessBean orderBean = new OrderAccessBean();

			// find the order entry from the order ref number (orders table)
			orderBean.setInitKey_orderId(String.valueOf(oldOrderRn));
			orderBean.instantiateEntity();
			updateOrderStatus(orderBean);

		} catch (Exception e) {
			ECTrace.trace(ECTraceIdentifiers.COMPONENT_CATALOG, CLASS_NAME, strMethodName,
					"No old order of id, " + oldOrderRn + " is found.");
		}

		OrderItemMoveCmd cmd = (OrderItemMoveCmd) CommandFactory.createCommand(OrderItemMoveCmd.NAME, getStoreId());

		TypedProperty tp = new TypedProperty();

		String s = String.valueOf(oldOrderRn);
		tp.put(OrderConstants.EC_FROM_ORDER_ID, s);
		tp.put(OrderConstants.EC_DELETE_IF_EMPTY, s);
		tp.put(ECConstants.EC_URL, "");
		cmd.setRequestProperties(tp);
		// getCommandContext().setRequestProperties(tp);
		cmd.setCommandContext(getCommandContext());

		try {
			cmd.execute();
		} catch (ECException e) {
			ECTrace.trace(ECTraceIdentifiers.COMPONENT_CATALOG, CLASS_NAME, strMethodName,
					"Exception: " + e.getMessage());
		}
		ECTrace.exit(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);
	}

	/**
	 * Method updateStatus.
	 * 
	 * @param orderBean
	 * @exception ECException
	 */
	private void updateOrderStatus(OrderAccessBean orderBean) throws ECException {
		final String strMethod = "updateOrderStatus";
		ECTrace.entry(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethod);
		try {
			String status_orders = orderBean.getStatus();
			if (status_orders.equalsIgnoreCase(ECMEOrderConstants.ORDER_SUBMITTED_TO_PROCUREMENT)) {
				orderBean.setStatus(ECMEOrderConstants.ORDER_PENDING);
				OrderItemAccessBean orderItemBean = new OrderItemAccessBean();

				Enumeration e = orderItemBean.findByOrder(orderBean.getOrderIdInEntityType());

				// multiple rows are possible in orderitems table for one order
				while (e.hasMoreElements()) {
					OrderItemAccessBean b = (OrderItemAccessBean) e.nextElement();
					String status_orderitems = b.getStatus();

					// change the status to "P" if it was "W" else return saying
					// that
					// it is in invalid status
					if (status_orderitems.equalsIgnoreCase(ECMEOrderConstants.ORDER_SUBMITTED_TO_PROCUREMENT)) {
						b.setStatus(ECMEOrderConstants.ORDER_PENDING);
					}
				}

			}

		} catch (NoResultException ex) {
			throw new ECSystemException(ECMessage._ERR_FINDER_EXCEPTION, getClass().getName(), strMethod, ex);
		}
		ECTrace.exit(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethod);
	}

	/**
	 * Set up the buyer and supplier mapping with
	 * BuyerSupplierMappingAccessbean. Updates variable catalogId, contractId,
	 * and memberGroupId with the values from the database. Find storeId and
	 * then update the command context with the newly found storeId.
	 * 
	 */
	protected void determineMappingProperties() {
		String strMethodName = "determineMappingProperties";

		try {
			// get the CatalogId
			BuyerSupplierMappingAccessBean mappingBean = new BuyerSupplierMappingAccessBean();

			mappingBean.setInitKey_iBuyerOrganizationId(String.valueOf(buyerId));
			mappingBean.setInitKey_iSupplierOrganizationId(String.valueOf(supplierId));
			mappingBean.setInitKey_protocolId(String.valueOf(protocolId));
			mappingBean.instantiateEntity();

			catalogId = mappingBean.getCatalogIdInEntityType();
			setContractId(mappingBean.getContractIdInEntityType());
			memberGroupId = mappingBean.getMemberGroupIdInEntityType();

			ECTrace.trace(ECTraceIdentifiers.COMPONENT_CATALOG, CLASS_NAME, strMethodName,
					"CATALOG_ID_VALUE = " + catalogId);
		} catch (Exception ex) {
			ECTrace.trace(ECTraceIdentifiers.COMPONENT_CATALOG, CLASS_NAME, strMethodName, "buyerId: " + buyerId
					+ " supplierId: " + supplierId + " protocolId: " + protocolId + " Exception: " + ex.getMessage());
		}

		setStoreId(StoreHelper.findStoreId(new Long(supplierId), catalogId, getContractId()));
		if ((getStoreId() != null) && (getStoreId().intValue() != ECConstants.EC_NO_STOREID.intValue())) {
			getCommandContext().setStoreId(getStoreId());

			StoreAccessBean storeAB = WcsApp.storeRegistry.find(getStoreId());
			ECTrace.trace(ECTraceIdentifiers.COMPONENT_ORDER, this.getClass().getName(), strMethodName,
					"storeId =" + getStoreId() + " store=" + storeAB);
			getCommandContext().setStore(storeAB);
		}

	}

	/**
	 * The main business logic, processes the PurchaseOrderRequest from the
	 * buyer organization using procurement system and creates an order.
	 *
	 * @exception ECException
	 */
	protected void doProcess() throws ECException {
		final String strMethodName = "doProcess()";
		ECTrace.entry(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);

		boolean rc = true;

		errorCode = checkDuplicateOrderRequest();

		// authenticate buyer supplier
		if (errorCode == ErrorConstants.NO_ERROR) {
			validCredentials = isValidCredentials();
		}
		if (validCredentials) {
			OrganizationAccessBean orgBean = (OrganizationAccessBean) JpaEntityAccessBeanCacheUtil
					.newJpaEntityAccessBean(OrganizationAccessBean.class);
			orgBean.setInitKey_memberId(getBuyerId().toString());

			// Access Control - Does the user has procurement buyer admin role
			// for this buyer organization
			// Needed since RegisterRequisitioner task command is not always
			// invoked
			checkIsAllowed(orgBean, "com.ibm.commerce.me.commands.BatchOrderRequestCmd");

			// Check whether the order is solicted
			// If yes orgReqId will be set to the original requisitioner id
			changeOrderStatus();

			if ((orgReqId == null) && (getRequisitionerId() != null)) {
				checkRegistration();
			}
		}

		// create Bill To Address
		if (this.errorCode == ErrorConstants.NO_ERROR) {
			CreateShippingBillingAddressCmd sbAddress = (CreateShippingBillingAddressCmd) CommandFactory
					.createCommand(CreateShippingBillingAddressCmd.NAME, commandContext.getStoreId());
			sbAddress.setCommandContext(getCommandContext());
			sbAddress.setAddressType(ECMEOrderConstants.BILLTO_ADDRESS_TYPE);
			sbAddress.setBillToAddress(ciData.getBillTo());
			sbAddress.setMemberId(usersId);
			sbAddress.execute();
			btAddressId = sbAddress.getAddressId();
			errorCode = sbAddress.getErrorCode();
			if (this.errorCode != ErrorConstants.NO_ERROR) {
				ECTrace.trace(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName,
						"Creating Bill to Address Failed:  [errorCode = " + errorCode + "]");
			} else {
				ECTrace.trace(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName,
						"Created Bill to Address:  [ AddressId = " + btAddressId + "]");
			}
		}

		// Create ShipTo Address if the shipTo is at the
		// Order Level

		if (this.errorCode == ErrorConstants.NO_ERROR && (ciData.getPOHeader().getShipTo() != null)) {
			CreateShippingBillingAddressCmd shipAddress = (CreateShippingBillingAddressCmd) CommandFactory
					.createCommand(CreateShippingBillingAddressCmd.NAME, getCommandContext().getStoreId());
			shipAddress.setCommandContext(getCommandContext());
			shipAddress.setAddressType(ECMEOrderConstants.SHIPTO_ADDRESS_TYPE);
			shipAddress.setShipToAddress(ciData.getPOHeader().getShipTo());
			shipAddress.setMemberId(usersId);
			shipAddress.execute();
			stAddressId = shipAddress.getAddressId();
			errorCode = shipAddress.getErrorCode();
			if (this.errorCode != ErrorConstants.NO_ERROR) {
				ECTrace.trace(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName,
						"Creating ShipTo Address failed  [errorCode = " + errorCode + "]");
			} else {
				ECTrace.trace(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName,
						"Successfully created shipto address [AddressId = " + stAddressId + "]");
			}
		}

		// Create the order items by calling OrderItemAdd command

		try {
			if (ciData.getPOItems() != null) {
				processOrderItems();
			}
		} catch (ECException e) {
			this.errorCode = ErrorConstants.ORDERITEM_CREATE_FAILED;
			throw e;
		}

		if (oldQuotes != null) {
			Enumeration enQuoteIds = oldQuotes.elements();
			while (enQuoteIds.hasMoreElements()) {
				Long delOrderId = (Long) enQuoteIds.nextElement();
				deleteOldOrder(delOrderId);
			}
		}

		// now call the order prepare command.
		Long orgUserId = getCommandContext().getUserId();
		Integer orgStoreId = getCommandContext().getStoreId();

		if ((this.errorCode == ErrorConstants.NO_ERROR) && rc) {

			/**
			 * For the POC purpose added the default Payment Instruction for COD
			 */
			TypedProperty tempRequestProperties = new TypedProperty();
			tempRequestProperties.put("billing_address_id", btAddressId);
			tempRequestProperties.put("policyId", "15506");
			tempRequestProperties.put("payMethodId", "COD");
			tempRequestProperties.put("piAmount", ciData.getPOHeader().getTotalAmount());
			tempRequestProperties.put("orderId", orderId);
			tempRequestProperties.put("URL", "PIAdd");

			PIAddCmd aPIAddCmd = (PIAddCmd) CommandFactory.createCommand("com.ibm.commerce.edp.commands.PIAddCmd",
					getStoreId());
			aPIAddCmd.setAccCheck(false);
			aPIAddCmd.setCommandContext((CommandContext) getCommandContext().clone());
			aPIAddCmd.setRequestProperties(tempRequestProperties);
			aPIAddCmd.execute();
			prepareOrder(orgStoreId);
		}
		if (this.errorCode == ErrorConstants.NO_ERROR) {
			processOrder(getStoreId());
		}

		// now restore the user id and store id in the command context.
		getCommandContext().setUserId(orgUserId);
		getCommandContext().setStoreId(orgStoreId);

		ECTrace.exit(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);

	}

	/**
	 * Gets the authentication type
	 * 
	 * @return an integer indicating the authentication type
	 */
	public Integer getAuthType() {
		return authType;
	}

	/**
	 * Gets the buyer id
	 * 
	 * @return id of the buyer
	 */
	public Long getBuyerId() {
		return new Long(buyerId);
	}

	/**
	 * Gets the contract id
	 * 
	 * @return the contract id
	 */
	public Long getContractId() {
		return contractId;
	}

	/**
	 * Gets duplicate address
	 * 
	 * @param addr
	 *            address to check whether there is a duplicate
	 */
	private Long getDuplicateAddress(Address addr) {
		if (hshDupAddress == null || addr == null) {
			return null;
		}

		Long index = null;
		Enumeration e = hshDupAddress.keys();

		while (e.hasMoreElements()) {
			Long temp = (Long) e.nextElement();
			if (addr.equals(hshDupAddress.get(temp))) {
				index = temp;
				break;
			}
		}
		return index;
	}

	/**
	 * Gets the organization unit id
	 * 
	 * @return the id identifying the organization
	 */
	public Long getOrganizationUnitId() {
		return organizationUnitId;
	}

	/**
	 * Gets the protocol id
	 * 
	 * @return id identifying the protocol
	 */
	public Integer getProtocolId() {
		return protocolId;
	}

	/**
	 * Gets the requisitioner id
	 * 
	 * @return requisitioner id
	 */
	public String getRequisitionerId() {
		return requisitionerId;
	}

	/**
	 * Gets the store id
	 * 
	 * @return the store id
	 */
	public Integer getStoreId() {
		return storeId;
	}

	/**
	 * Gets the supplier id
	 * 
	 * @return the supplier id
	 */
	public Long getSupplierId() {
		return new Long(supplierId);
	}

	/**
	 * Initializes the input data
	 */
	protected void initializeInputData() {
		ciData = new CIDataImpl(getCommandContext());
	}

	/**
	 * A user is generic till he logs on, so returns true.
	 *
	 * @param none
	 * @return true if this command can be executed by a generic user false if
	 *         this command cannot be executed by a generic user
	 */
	public boolean isGeneric() {
		return true;
	}

	/**
	 * Checks the credentials of the user who has submitted this order.
	 * 
	 * @return true if the credentials of the user is valid.
	 * @exception ECException
	 */
	protected boolean isValidCredentials() throws ECException {
		String strMethodName = "isValidCredentials";
		ECTrace.entry(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);

		try {

			AuthenticationHelperCmd cmd = (AuthenticationHelperCmd) CommandFactory
					.createCommand(AuthenticationHelperCmd.NAME, commandContext.getStoreId());
			cmd.setBuyerCredentials(ciData.getBuyerCredentials());
			cmd.setSupplierCredentials(ciData.getSupplierCredentials());
			cmd.setMarketPlaceBuyerCredentials(ciData.getMarketPlaceCredentials());
			cmd.setSessionInfo(ciData.getSessionInfo());
			cmd.setCommandContext(commandContext);
			cmd.setAuthType(getAuthType().intValue());
			cmd.setProtocolId(getProtocolId());

			cmd.execute();
			validCredentials = cmd.isValidCredentials();
			this.errorCode = cmd.getErrorCode();
			if (!validCredentials) {
				ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName,
						"Validating credentials failed");
			} else {

				buyerId = cmd.getBuyerId();
				supplierId = cmd.getSupplierId();
				setBuyerRequestProperties();

				determineMappingProperties();

			}
		} catch (ECException expCmd) {
			ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName,
					"Exeption occurred" + expCmd);
			ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);
			throw expCmd;
		}
		ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);
		return validCredentials;
	}

	/**
	 * If the checkParamateres are OK, authenticate the buyer and supplier
	 * credentials. If the order is solicit order get the user id from the
	 * shopping cart id and change the shopping cart status from <code>I</code>
	 * to <code>H</code>. or else register the user.
	 * 
	 * @exception CommandException.
	 */
	public void performExecute() throws ECException {
		final String strMethodName = "performExecute";
		ECTrace.entry(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);
		boolean failed = false;

		if (!checkParametersOk) {
			ECTrace.trace(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName,
					"validateParameters failed");
			ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);
			return;
		}
		responseProperties = new TypedProperty();

		String errorView = StoreHelper.getResponseErrorView(getProtocolId().toString(), getBuyerId().toString(),
				"PurchaseOrderResponse");
		if (errorView == null) {
			errorView = errorTask;
		}

		try {
			doProcess();
		} catch (ECSystemException e) {

			// responseProperties.put(ECConstants.EC_VIEWTASKNAME, errorView);
			if (this.errorCode == ErrorConstants.NO_ERROR) {
				this.errorCode = ErrorConstants.ORDER_CREATE_FAILED;
			}
			failed = true;
		} catch (ECApplicationException e) {
			if (this.errorCode == ErrorConstants.NO_ERROR) {
				this.errorCode = ErrorConstants.ORDER_CREATE_FAILED;
			}
			// responseProperties.put(ECConstants.EC_VIEWTASKNAME, errorView);
			failed = true;
		}
		if (this.errorCode != ErrorConstants.NO_ERROR) {
			failed = true;
		}

		if (!failed) {
			String view = StoreHelper.getResponseViewName(getProtocolId().toString(), getBuyerId().toString(),
					"PurchaseOrderResponse");
			if (view == null) {
				view = viewTask;
			}

			responseProperties.put(ECConstants.EC_VIEWTASKNAME, view);
		} else {
			responseProperties.put(ECConstants.EC_VIEWTASKNAME, errorView);
		}

		Status stat = Status.getStatus(errorCode, getCommandContext().getLocale());

		responseProperties.put(ECMEUserConstants.STATUS_CODE, "" + stat.getCode());
		responseProperties.put(ECMEUserConstants.STATUS_TEXT, stat.getText());
		responseProperties.put(ECMEUserConstants.STATUS_MESSAGE, stat.getMessage());
		responseProperties.put(ECMEUserConstants.PROCUREMENT_ERROR_CODE, Integer.toString(errorCode));
		responseProperties.put(ECMEUserConstants.ORDERS_ID, String.valueOf(this.orderId));
		ECTrace.exit(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);
	}

	/**
	 * Business logic to prepare an order
	 * 
	 * @param aStoreId
	 *            store id
	 * @exception com.ibm.commerce.exception.ECException
	 *                Raised if order cannot be prepared
	 */
	protected void prepareOrder(Integer aStoreId) throws ECException {
		String strMethodName = "prepareOrder";

		ECTrace.entry(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);

		OrderAccessBean abOrder = new OrderAccessBean();
		abOrder.setInitKey_orderId(String.valueOf(orderId));

		Vector vOrder = new Vector(1);
		vOrder.add(abOrder);

		try {
			PrepareProcurementOrderCmd orderPrepare = (PrepareProcurementOrderCmd) CommandFactory
					.createCommand(PrepareProcurementOrderCmd.NAME, aStoreId);

			orderPrepare.setCommandContext(getCommandContext());
			orderPrepare.setOrders(vOrder);
			orderPrepare.execute();
		} catch (ECException expCmd) {
			this.errorCode = ErrorConstants.ORDER_PREPARE_FAILED;
			throw expCmd;

		}

		ECTrace.exit(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);
	}

	/**
	 * Business logic to process an order
	 * 
	 * @param orgStoreId
	 *            store id
	 * @exception com.ibm.commerce.exception.ECException
	 *                Raised if order cannot be processed
	 */
	protected void processOrder(Integer orgStoreId) throws ECException {
		final String strMethodName = "processOrder";

		ECTrace.entry(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);

		// if there is no error call OrderComplete command
		PaymentInfo info = ciData.getPOHeader().getPayMethod();

		TypedProperty procOrderReqParm = new TypedProperty();
		/*
		 * if (info != null) { Calendar cal = Calendar.getInstance();
		 * cal.setTime(info.getExpiration());
		 * 
		 * procOrderReqParm.put("cardNumber", info.getCardNumber());
		 * procOrderReqParm.put("cardBrand", info.getCardName());
		 * procOrderReqParm.put("cardExpiryMonth", new
		 * Integer(cal.get(2)).toString());
		 * procOrderReqParm.put("cardExpiryYear", new
		 * Integer(cal.get(1)).toString()); } else {
		 * procOrderReqParm.put("cardNumber", "2222222222");
		 * procOrderReqParm.put("cardBrand", "VISA");
		 * procOrderReqParm.put("cardExpiryMonth", "12");
		 * procOrderReqParm.put("cardExpiryYear", "2025"); }
		 */

		UsablePaymentTCListDataBean payTCBean = new UsablePaymentTCListDataBean();

		payTCBean.setOrderId(orderId);

		DataBeanManager.activate(payTCBean, getCommandContext());
		PaymentTCInfo payTCInfo[] = payTCBean.getPaymentTCInfo();

		String tcId = null;
		for (int i = 0; i < payTCInfo.length; i++) {
			if (payTCInfo[i].getPolicyName().equalsIgnoreCase("COD")) {
				procOrderReqParm.put(ECContractCmdConstants.EC_CONTRACT_TC_ID, payTCInfo[i].getTCId());
				procOrderReqParm.put(ECContractCmdConstants.EC_POLICY_ID, payTCInfo[i].getPolicyId());
			}
		}

		ProcessOrderCmd processOrderCmd = (ProcessOrderCmd) CommandFactory.createCommand(ProcessOrderCmd.NAME,
				orgStoreId);
		processOrderCmd.setOrderRn(orderId);
		processOrderCmd.setBillToRn(btAddressId);

		getCommandContext().setRequestProperties(procOrderReqParm);
		processOrderCmd.setCommandContext(getCommandContext());

		processOrderCmd.setPoNumber(ciData.getPOHeader().getOrderId());

		try {
			processOrderCmd.execute();
		} catch (ECException expCmd) {
			// TBD : do we have to roll back if order complete fails ?
			ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName,
					"ORDER_PROCESS_FAILED [orderId = " + orderId + "]");
			this.errorCode = ErrorConstants.ORDER_PROCESS_FAILED;
			throw expCmd;

		}
		ECTrace.exit(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);

	}

	/**
	 * Process the shopping cart items
	 * 
	 * @exception ECException
	 */
	protected void processOrderItems() throws ECException {
		String strMethodName = "processOrderItems";

		ECTrace.entry(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);

		hshDupAddress = new Hashtable();

		Hashtable hshAddress = new Hashtable();
		Hashtable hshPartNumber = new Hashtable();

		Hashtable hshContract = new Hashtable();

		Hashtable hshQuantity = new Hashtable();
		Hashtable hshShipMode = new Hashtable();

		// optional item specification attribute
		Hashtable hshAttrName = new Hashtable();

		// optional item specification value
		Hashtable hshAttrValue = new Hashtable();

		// shipping mode for the whole order if the protocol does not
		// giver ship modes for each item.
		String orderLevelShipMode = ciData.getPOHeader().getShipModeId();

		Vector poItems = ciData.getPOItems();

		Enumeration items = poItems.elements();

		int i = 0;
		while (items != null && items.hasMoreElements()) {
			i++;

			PurchaseOrderItem poItem = (PurchaseOrderItem) items.nextElement();

			// if shipto address is not at the order level create the addresses
			// for each
			// order item.
			if (ciData.getPOHeader().getShipTo() == null) {
				stAddressId = getDuplicateAddress(poItem.getShipTo());
				if (stAddressId == null) {
					CreateShippingBillingAddressCmd shipAddress = (CreateShippingBillingAddressCmd) CommandFactory
							.createCommand(CreateShippingBillingAddressCmd.NAME, commandContext.getStoreId());
					shipAddress.setAddressType(ECMEOrderConstants.SHIPTO_ADDRESS_TYPE);
					shipAddress.setShipToAddress(poItem.getShipTo());
					shipAddress.setMemberId(usersId);
					shipAddress.setCommandContext(getCommandContext());
					shipAddress.execute();
					stAddressId = shipAddress.getAddressId();
					errorCode = shipAddress.getErrorCode();

					if (errorCode == ErrorConstants.NO_ERROR) {
						hshDupAddress.put(stAddressId, poItem.getShipTo());
					}
				}
			}

			hshAddress.put(new Integer(i), String.valueOf(stAddressId));

			double qty = poItem.getQuantityInDouble();

			hshPartNumber.put(new Integer(i), poItem.getItemId());
			hshQuantity.put(new Integer(i), String.valueOf(qty));

			String shipmodeId;

			shipmodeId = poItem.getShipModeId();

			if (shipmodeId == null) {
				shipmodeId = orderLevelShipMode;
			}

			if (shipmodeId != null) {
				hshShipMode.put(new Integer(i), shipmodeId);
			}

			if (poItem.getItemAttrName() != null) {
				hshAttrName.put(new Integer(i), poItem.getItemAttrName());
			}

			if (poItem.getItemAttrValue() != null) {
				hshAttrValue.put(new Integer(i), poItem.getItemAttrValue());
			}

			if (getContractId() != null) {
				hshContract.put(new Integer(i), new String[] { getContractId().toString() });
			}

		}

		OrderItemAddCmd ordItemCmd = (OrderItemAddCmd) CommandFactory.createCommand(OrderItemAddCmd.NAME,
				getCommandContext().getStoreId());

		ordItemCmd.setPartNumber(hshPartNumber);
		ordItemCmd.setQuantity(hshQuantity);

		if (hshShipMode.size() > 0) {
			ordItemCmd.setShipmodeId(hshShipMode);
		}

		if (hshAddress.size() > 0) {
			ordItemCmd.setAddressId(hshAddress);
		}

		if (hshAttrName.size() > 0) {
			ordItemCmd.setAttrName(hshAttrName);
		}

		if (hshAttrValue.size() > 0) {
			ordItemCmd.setAttrValue(hshAttrValue);
		}

		if (getContractId() != null) {
			ordItemCmd.setContractId(hshContract);
		}

		ordItemCmd.setCommandContext(getCommandContext());
		ordItemCmd.execute();

		String orderItemIds[] = ordItemCmd.getOrderItemIds();
		String orderIds[] = ordItemCmd.getOrderIds();

		if (orderIds.length == 1) {
			orderId = Long.valueOf(orderIds[0]);
		}

		createOrderMessagingExtensionRecord(orderId);

		Enumeration items2 = poItems.elements();
		for (int j = 0; j < orderItemIds.length; j++) {

			PurchaseOrderItem poItem = (PurchaseOrderItem) items2.nextElement();

			try {
				OrderItemMessagingExtensionAccessBean b2bItem = new OrderItemMessagingExtensionAccessBean(
						Long.valueOf(orderItemIds[j]));

				// split the comment into two columns if larger that max lenght
				String s = poItem.getComments();
				if (s != null && s.length() > ECMEOrderConstants.COMMENT_LENGTH) {
					String s1 = s.substring(0, ECMEOrderConstants.COMMENT_LENGTH);
					String s2 = s.substring(ECMEOrderConstants.COMMENT_LENGTH + 1);
					b2bItem.setComments1(s1);
					b2bItem.setComments2(s2);
				} else {
					b2bItem.setComments1(s);
				}

				if (poItem.getRequestedDeliveryDate() != null) {
					b2bItem.setRequestedShipTime(new Timestamp(poItem.getRequestedDeliveryDate().getTime()));
				}
			} catch (Exception e) {

				ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName,
						"Exception when creating the ORDIMEEXTN table entry (order item messaging extension record) "
								+ e);
				errorCode = ErrorConstants.ORDERITEM_CREATE_FAILED;
			}
		}

		ECTrace.exit(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);

	}

	/**
	 * Changes the Shopping cart status from "I" to "H" that is, requested
	 * status.
	 * 
	 * @param shoppingCartId
	 *            id identifying a shopping cart session
	 * @throws ECException
	 *             if an exception occurs when retrieving the user information.
	 */
	protected void retrieveUserInfo(Long shoppingCartId) throws ECException {

		orgReqId = null;
		String strMethodName = "retrieveUserInfo";
		ECTrace.entry(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);
		try {
			OrderAccessBean orderBean = new OrderAccessBean();
			orderBean.setInitKey_orderId(String.valueOf(shoppingCartId));
			orderBean.instantiateEntity();
			usersId = orderBean.getMemberIdInEntityType();

			BusinessProfileAccessBean busBean = new BusinessProfileAccessBean();
			busBean.setInitKey_userId(String.valueOf(usersId));
			orgReqId = busBean.getRequistionerId();

		} catch (NoResultException e) {
			ECTrace.trace(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName,
					"Finder Exception: " + e);
			this.errorCode = ErrorConstants.INVALID_QUOTE_NUMBER;

		}
		if (orgReqId != null) {
			getCommandContext().setUserId(usersId);
		}

		ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, getClass().getName(), strMethodName);

	}

	/**
	 * Sets the authentication type
	 * 
	 * @param anAuthType
	 *            type of authentication mode
	 */
	public void setAuthType(Integer anAuthType) {
		this.authType = anAuthType;

	}

	/**
	 * Sets the buyer organization id
	 * 
	 * @param aBuyerId
	 *            the buyer id
	 */
	public void setBuyerId(Long aBuyerId) {
		this.buyerId = aBuyerId.longValue();
	}

	/**
	 * Sets the buyer request properties based on the protocol id and buyer id
	 * 
	 */
	protected void setBuyerRequestProperties() {
		if ((getProtocolId() == null) || (getBuyerId() == null)) {
			return;
		}

		String orgUnitName = null;
		String reqId = null;
		try {

			ProcurementBuyerProfileAccessBean aBean = new ProcurementBuyerProfileAccessBean();
			aBean.setInitKey_protocolId(getProtocolId().toString());
			aBean.setInitKey_organizationId(getBuyerId().toString());
			aBean.instantiateEntity();

			reqId = requestProperties.getString(aBean.getReqIdParamName(), null);
			if (reqId == null) {
				reqId = requestProperties.getString(XMLConstants.REQ_ID, null);
			}

			setRequisitionerId(reqId);
			ciData.getSessionInfo().setReqId(reqId);
			orgUnitName = requestProperties.getString(aBean.getOrganizationUnitParamName(), null);

		} catch (Exception e) {
			ECTrace.trace(ECTraceIdentifiers.COMPONENT_CATALOG, CLASS_NAME, "setBuyerRequestProperties()",
					"protocolId: " + getProtocolId() + " buyerId: " + getBuyerId() + " Exception: " + e.getMessage());
		}
	}

	/**
	 * Sets the contract id
	 * 
	 * @param aContractId
	 *            the contract id
	 */
	public void setContractId(Long aContractId) {
		this.contractId = aContractId;
	}

	/**
	 * Sets the organization unit id
	 * 
	 * @param orgUnit
	 *            the organization unit id
	 */
	public void setOrganizationUnitId(Long orgUnit) {
		organizationUnitId = orgUnit;
	}

	/**
	 * Sets the protocol id
	 * 
	 * @param aProtocolId
	 *            the protocol id
	 */
	public void setProtocolId(Integer aProtocolId) {
		this.protocolId = aProtocolId;
	}

	/**
	 * The WebController calls the setRequestProperties method before invoking
	 * the execute method in this command. Set the <code>POData</code> by
	 * calling <code>setPOData()</code> passing requestProperties.
	 * 
	 * @param requestProperties
	 *            typed Property that contains the POData
	 * @exception ECException
	 */
	public void setRequestProperties(TypedProperty p) throws ECException {
		final String strMethodName = "setRequestProperties";
		ECTrace.entry(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);

		requestProperties = p;

		initializeInputData();
		try {
			ciData.setPOData(requestProperties);
		} catch (Exception e) {
			throw new ECApplicationException(ECMessage._ERR_GENERIC, CLASS_NAME, strMethodName,
					ECMessageHelper.generateMsgParms(e.toString()));
		}
		ECTrace.exit(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);
	}

	/**
	 * Sets the requisitioner id
	 * 
	 * @param aRequisitionerId
	 *            the requisitioner id
	 */
	public void setRequisitionerId(String aRequisitionerId) {
		this.requisitionerId = aRequisitionerId;
	}

	/**
	 * Sets the status if the order is solicited (true or false)
	 * 
	 * @param status
	 *            true if order is solicited false if otherwise
	 */
	private void setSolicitedOrder(boolean status) {
		solicitedOrder = status;
	}

	/**
	 * Sets the store id
	 * 
	 * @param aStoreId
	 *            the store id
	 */
	public void setStoreId(Integer aStoreId) {
		this.storeId = aStoreId;
	}

	/**
	 * Sets the supplier id
	 * 
	 * @param aSupplierId
	 *            the supplier id
	 */
	public void setSupplierId(Long aSupplierId) {
		this.supplierId = aSupplierId.longValue();
	}

	/**
	 * Validate parameters, the validation includes checking whether buyer
	 * credential is null or empty and whether the protocol valid.
	 * 
	 * @exception ECException.
	 */
	public void validateParameters() throws ECException {
		String strMethodName = "validateParameters";

		ECTrace.entry(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);

		if (ciData.getBuyerCredentials() != null && ciData.getSupplierCredentials() != null) {
			if (ciData.getBuyerCredentials().getCode() != null
					&& ciData.getBuyerCredentials().getCode().trim().length() != 0
					&& ciData.getBuyerCredentials().getCodeDomain() != null
					&& ciData.getBuyerCredentials().getCodeDomain().trim().length() != 0
					&& ciData.getSupplierCredentials().getCode() != null
					&& ciData.getSupplierCredentials().getCode().trim().length() != 0
					&& ciData.getSupplierCredentials().getCodeDomain() != null
					&& ciData.getSupplierCredentials().getCodeDomain().trim().length() != 0) {
				checkParametersOk = true;
			}
		}

		try {

			ProcurementProtocolAccessBean aBean = new ProcurementProtocolAccessBean();

			protocolBean = aBean.findByProtocolNameAndVersion(ciData.getProtocolName(), ciData.getProtocolVersion());
			protocolId = protocolBean.getProtocolIdInEntityType();
			setAuthType(protocolBean.getAuthenticationTypeInEntityType());

		} catch (Exception e) {

			checkParametersOk = false;
		}

		ECTrace.exit(ECTraceIdentifiers.COMPONENT_ORDER, getClass().getName(), strMethodName);
	}
}
