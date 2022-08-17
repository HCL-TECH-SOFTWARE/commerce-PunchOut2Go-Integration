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
import java.util.Vector;

import javax.persistence.EntityExistsException;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;

import com.ibm.commerce.accesscontrol.AccessVector;
import com.ibm.commerce.base.objects.ServerJDBCHelperBean;
import com.ibm.commerce.command.CommandFactory;
import com.ibm.commerce.command.TaskCommandImpl;
import com.ibm.commerce.datatype.CacheRule;
import com.ibm.commerce.datatype.TypedProperty;
import com.ibm.commerce.dynacache.CacheConstants;
import com.ibm.commerce.ejb.helpers.SessionBeanHelper;
import com.ibm.commerce.exception.ECApplicationException;
import com.ibm.commerce.exception.ECException;
import com.ibm.commerce.exception.ECSystemException;
import com.ibm.commerce.foundation.internal.server.services.registry.StoreConfigurationRegistry;
import com.ibm.commerce.me.common.ECMEMessage;
import com.ibm.commerce.me.common.ECMEUserConstants;
import com.ibm.commerce.me.common.ErrorConstants;
import com.ibm.commerce.me.common.Status;
import com.ibm.commerce.me.datatype.SessionInfo;
import com.ibm.commerce.me.datatype.StoreHelper;
import com.ibm.commerce.member.helpers.MemberRegistrationAttributesHelper;
import com.ibm.commerce.persistence.JpaEntityAccessBeanCacheUtil;
import com.ibm.commerce.ras.ECMessage;
import com.ibm.commerce.ras.ECMessageHelper;
import com.ibm.commerce.ras.ECTrace;
import com.ibm.commerce.ras.ECTraceIdentifiers;
import com.ibm.commerce.user.objects.BusinessProfileAccessBean;
import com.ibm.commerce.user.objects.MemberAccessBean;
import com.ibm.commerce.user.objects.MemberGroupMemberAccessBean;
import com.ibm.commerce.user.objects.MemberRoleAccessBean;
import com.ibm.commerce.user.objects.OrganizationAccessBean;
import com.ibm.commerce.user.objects.UserAccessBean;
import com.ibm.commerce.user.objects.UserRegistryAccessBean;
import com.ibm.commerce.usermanagement.commands.UserRegistrationAdminAddCmd;
import com.ibm.commerce.util.wrapper.AES128Cryptx;

/**
 * This is the implementation of RegisterRequisitionerCmd. It retrieves the
 * requisitioning user if they are already registered, and registers them as a
 * new requisitioning user if they are not registered. The
 * <code>RegisterRequisitioner</code> command is called by the
 * <code>PunchOutSetup</code> and <code>BatchOrderRequest</code> commands after
 * successfully authenticating the <code>PunchOutSetupRequest</code>
 */
public class RegisterRequisitionerExtCmdImpl extends TaskCommandImpl implements RegisterRequisitionerCmd {
	/**
	 * Copyright statement.
	 */
	public static final String COPYRIGHT = "(c) Copyright International Business Machines Corporation 1996,2008";
	private static final String CLASS_NAME = RegisterRequisitionerExtCmdImpl.class.getName();

	private static final String TYPE_REGISTERED_USER = "R";

	private static final String REGISTRATION_QUALIFIER = "ProcurementRegistration";

	// are the parameters passed ok ?
	private boolean checkParametersOk = false;

	// flag to determine whether registration was successful or not
	private boolean registrationSuccessful = false;

	// buyer requisitioner ID will be stored here
	private String reqId = null;

	// buyer requisitioner name will be stored here
	private String reqName = null;

	// buyer requisitioner email will be stored here
	private String email = null;

	// buyer department name will be stored here
	private String deptName = null;

	// postbackurl will be store here
	private String postbackUrl = null;

	// session id (eg buyer cookie )will be stored here
	private String sessionId = null;

	// session type will be stored here
	private String sessionType = null;

	// order status url will be stored here
	private String orderStatusUrl = null;

	// session info objects contains data pertaining to session
	private SessionInfo sessionInfo = null;

	// users Id
	private Long userId = null;

	// the id of the buyer organization
	private static final long DEFAULT_BUYER_ID = -1L;

	private long buyerId = DEFAULT_BUYER_ID;

	// the id of the supplier organization
	private long supplierId = -1L;
	private Integer protocolId = null;

	private static final int CHANNEL_MGR = -26;

	/**
	 * The lock object for updating BUSPROF table.
	 */
	protected static Object critsect = new Object();

	/**
	 * create an entry in the MemberGroupMember table for the new user.
	 *
	 * @param none
	 * @return void
	 */
	private void createMemberGroupMember() {
		final String strMethodName = "createMemberGroupMember";
		final boolean bTrace = ECTrace.traceEnabled(ECTraceIdentifiers.COMPONENT_USER);
		if (bTrace) {
			ECTrace.entry(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, strMethodName);
		}

		if (buyerId != -1L && supplierId != -1L && userId != null) {
			Long memberGroup = StoreHelper.findMemberGroupId(buyerId, supplierId, getProtocolId());

			if (memberGroup != null) {
				try {
					new MemberGroupMemberAccessBean(memberGroup, userId, "0");

				} catch (Exception e) {

					if (bTrace) {
						ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, strMethodName,
								"EXCEPTION_OCCURED" + e);
					}
				}
			}
		}
		if (bTrace) {
			ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, strMethodName);
		}
	}

	private void createNewUser() throws ECException {
		final String strMethodName = "createNewUser";

		final boolean bTrace = ECTrace.traceEnabled(ECTraceIdentifiers.COMPONENT_USER);
		if (bTrace) {
			ECTrace.entry(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, strMethodName);
		}

		Long lBuyOrgId = new Long(buyerId);

		// If requisitioner is not registered then create a new one
		try {

			UserAccessBean userBean = null;
			boolean isRequisitionerReg = false;

			synchronized (critsect) {
				// Try to find the requisitioner using requisitionerId
				BusinessProfileAccessBean aBean = findBusinessProfile(reqId, buyerId);

				if (aBean != null) {

					isRequisitionerReg = true;
					if (bTrace) {
						ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, strMethodName,
								"Found Requisitioner with Requisitioner ID = " + reqId);
					}

					setUsersId(aBean.getUserIdInEntityType());
					UpdateSessionInfo();

					if (bTrace) {
						ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, strMethodName,
								new Boolean(registrationSuccessful));
					}
					return;
				}

				OrganizationAccessBean orgABean = (OrganizationAccessBean) JpaEntityAccessBeanCacheUtil
						.newJpaEntityAccessBean(OrganizationAccessBean.class);
				orgABean.setInitKey_memberId(String.valueOf(lBuyOrgId));
				orgABean.instantiateEntity();
				String orgNameWithType = orgABean.getOrgEntityType().toLowerCase() + "="
						+ orgABean.getOrganizationName();

				// Create Requisitioner user
				TypedProperty createUserRequestProperties = new TypedProperty();
				createUserRequestProperties.put("URL", "NoURL");
				createUserRequestProperties.put("logonId", reqId);

				StoreConfigurationRegistry storeConfRegistry = StoreConfigurationRegistry.getSingleton();
				String reqUserDefaultPasswordEncrypted = storeConfRegistry.getValue(getStoreId(),
						"Requisitioner_User_Default_Password");
				String reqUserDefaultPassword = AES128Cryptx.decrypt(reqUserDefaultPasswordEncrypted, null);

				createUserRequestProperties.put("logonPassword", reqUserDefaultPassword);
				createUserRequestProperties.put("logonPasswordVerify", reqUserDefaultPassword);
				createUserRequestProperties.put("storeId", getStoreId());
				createUserRequestProperties.put("parentMember", orgNameWithType);
				createUserRequestProperties.put("profileType", "B");
				createUserRequestProperties.put("organizationDistinguishedName", orgNameWithType);
				createUserRequestProperties.put("appendRootOrganizationDN", true);
				createUserRequestProperties.put("email1", email);
				createUserRequestProperties.put("requistionerId", reqId);
				createUserRequestProperties.put("lastName", reqName);

				// Create Requisitioner User
				UserRegistrationAdminAddCmd UserRegAdminAddapi = (UserRegistrationAdminAddCmd) CommandFactory
						.createCommand("com.ibm.commerce.usermanagement.commands.UserRegistrationAdminAddCmd",
								getStoreId());
				if (UserRegAdminAddapi == null) {
					throw new ECApplicationException(ECMessage._ERR_CMD_CMD_NOT_FOUND, getClass().getName(),
							strMethodName);
				}

				UserRegAdminAddapi.setCommandContext(getCommandContext());
				UserRegAdminAddapi.setRequestProperties(createUserRequestProperties);
				UserRegAdminAddapi.setDefaultProperties(new TypedProperty());
				UserRegAdminAddapi.execute();
				TypedProperty createUserResponseProperties = UserRegAdminAddapi.getResponseProperties();

				setUsersId(createUserResponseProperties.getLong("userId"));

			}

			UpdateSessionInfo();

			// assign Procurement Buyer role
			MemberRoleAccessBean memRoleBean = new MemberRoleAccessBean(getUsersId(), new Integer(CHANNEL_MGR),
					lBuyOrgId);

			Vector results = MemberRegistrationAttributesHelper.getResolvedRolesForNewUser(getUsersId(),
					getStoreId().toString(), REGISTRATION_QUALIFIER);

			if (results != null && results.size() > 0) {
				for (int i = 0; i < results.size(); i++) {
					Object[] objArray = (Object[]) results.get(i);
					Integer roleId = (Integer) objArray[0];
					Long orgId = (Long) objArray[1];
					memRoleBean = new MemberRoleAccessBean(getUsersId(), roleId, orgId);
				}
			}

			// now create a memberGroupMember for the new user.
			createMemberGroupMember();

			// update the state of the member to be "1"
			MemberAccessBean memBean = new MemberAccessBean();
			memBean.setInitKey_memberId(getUsersId().toString());
			memBean.setState("1");
			ServerJDBCHelperBean abFlush =

					(ServerJDBCHelperBean) SessionBeanHelper.lookupSessionBean(ServerJDBCHelperBean.class);
			abFlush.flush();
			registrationSuccessful = true;
		} catch (NoResultException e) {
			throw new ECSystemException(ECMessage._ERR_FINDER_EXCEPTION, CLASS_NAME, strMethodName,
					ECMessageHelper.generateMsgParms(e.toString()), e, true);
		} catch (EntityExistsException e) {
			throw new ECSystemException(ECMessage._ERR_CREATE_EXCEPTION, CLASS_NAME, strMethodName,
					ECMessageHelper.generateMsgParms(e.toString()), e, true);
		} catch (PersistenceException e) {
			throw new ECSystemException(ECMessage._ERR_REMOTE_EXCEPTION, CLASS_NAME, strMethodName,
					ECMessageHelper.generateMsgParms(e.toString()), e, true);
		}

		if (bTrace) {
			ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, strMethodName,
					new Boolean(registrationSuccessful));
		}
	}

	/**
	 * Generate Password for the new user.
	 * 
	 * @param none
	 * @return byte[]
	 */
	private byte[] generatePassword() throws ECSystemException {
		final String strMethodName = "generatePassword";

		final boolean bTrace = ECTrace.traceEnabled(ECTraceIdentifiers.COMPONENT_USER);
		if (bTrace) {
			ECTrace.entry(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, strMethodName);
		}

		byte[] password = null;
		try {
			Long aUserId = getCommandContext().getUserId();

			UserRegistryAccessBean userRegBean = (UserRegistryAccessBean) JpaEntityAccessBeanCacheUtil
					.newJpaEntityAccessBean(UserRegistryAccessBean.class);
			userRegBean.setInitKey_userId(String.valueOf(aUserId));
			userRegBean.instantiateEntity();
			password = userRegBean.getLogonPassword();

		} catch (NoResultException e) {
			throw new ECSystemException(ECMessage._ERR_FINDER_EXCEPTION, CLASS_NAME, strMethodName,
					ECMessageHelper.generateMsgParms(e.toString()), e, true);
		}

		if (bTrace) {
			ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, strMethodName);
		}
		return password;
	}

	/**
	 * Generate salt for the new user.
	 * 
	 * @param none
	 * @return String
	 */
	private String generateSalt() throws ECSystemException {
		final String strMethodName = "generateSalt";

		boolean bTrace = ECTrace.traceEnabled(ECTraceIdentifiers.COMPONENT_USER);
		if (bTrace) {
			ECTrace.entry(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, strMethodName);
		}

		String salt = null;
		try {
			Long aUserId = getCommandContext().getUserId();

			UserRegistryAccessBean userRegBean = (UserRegistryAccessBean) JpaEntityAccessBeanCacheUtil
					.newJpaEntityAccessBean(UserRegistryAccessBean.class);
			userRegBean.setInitKey_userId(String.valueOf(aUserId));
			userRegBean.instantiateEntity();
			salt = userRegBean.getSalt();

		} catch (NoResultException e) {
			throw new ECSystemException(ECMessage._ERR_FINDER_EXCEPTION, CLASS_NAME, strMethodName,
					ECMessageHelper.generateMsgParms(e.toString()), e, true);
		}

		if (bTrace) {
			ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, strMethodName);
		}
		return salt;
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
	 * This method gets a list of all resources that contain organizations.
	 * 
	 * @return <samp>resourceList</samp> The resource list that contains
	 *         organizations.
	 * @throws ECException
	 */
	public AccessVector getResources() throws ECException {
		OrganizationAccessBean orgAB = (OrganizationAccessBean) JpaEntityAccessBeanCacheUtil
				.newJpaEntityAccessBean(OrganizationAccessBean.class);
		orgAB.setInitKey_memberId((new Long(buyerId)).toString());
		return new AccessVector(orgAB);
	}

	/**
	 * This method gets the user ID.
	 *
	 * @return userId The user ID
	 */
	public Long getUsersId() {
		return this.userId;
	}

	/**
	 * This method checks if a requisitioner registered successfully. Once the
	 * command is completed, this method is called to check whether the
	 * registration of the requisitioner succeeded.
	 *
	 * @return true If the requisitioner is registered successfully; false
	 *         otherwise.
	 */
	public boolean isRegisteredSuccessfully() {
		return registrationSuccessful;
	}

	/**
	 * The business logic for this task command. This method will verify the
	 * required parameters and then lookup the requisitioner using the
	 * requisitioner ID. If the requisitioner is registered then update the
	 * <samp>postbackUrl</samp>, <samp>sessionId</samp>,
	 * <samp>sessionType</samp>, <samp>orderStatusUrl</samp> and commit the
	 * database changes.
	 *
	 * @exception ECException
	 *                Raised when application errors occur.
	 */
	public void performExecute() throws ECException {
		final String strMethodName = "performExecute";
		final boolean bTrace = ECTrace.traceEnabled(ECTraceIdentifiers.COMPONENT_USER);
		if (bTrace) {
			ECTrace.entry(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, strMethodName);
		}

		CacheRule.issueBaseCacheInvalidations(CacheConstants.EC_CACHE_CUSTOMERS_COLLECTION);

		if (!checkParametersOk) {
			if (bTrace) {
				ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, strMethodName,
						"validateParameters failed");
				ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, strMethodName);
			}
			return;
		}

		if (bTrace) {
			ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, strMethodName,
					"Register Requisitioner command parameters: requisitionerId=" + reqId);
		}

		// Try to find the requisitioner using requisitionerId
		BusinessProfileAccessBean aBean = findBusinessProfile(reqId, buyerId);

		// if requisitioner is registered then update the postbackUrl
		// sessionId, sessionType & the orderStatusUrl amd commit
		if (aBean != null) {
			setUsersId(aBean.getUserIdInEntityType());
			UpdateSessionInfo();
		} else {
			createNewUser();
		}

		if (bTrace) {
			ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, strMethodName);
		}
	}

	/**
	 * This method sets the buyer ID (ID of the buyer organization).
	 *
	 * @param aBuyerId
	 *            The buyer ID.
	 */
	public void setBuyerId(long aBuyerId) {
		this.buyerId = aBuyerId;
	}

	/**
	 * This method sets the department name (if any) to which the requisitioner
	 * belongs to.
	 *
	 * @param aDeptName
	 *            The department name.
	 */
	public void setDeptName(String aDeptName) {
		this.deptName = aDeptName;
	}

	/**
	 * This method sets the order status URL.
	 *
	 * @param anOrderStatusUrl
	 *            The order status URL.
	 */
	public void setOrderStatusUrl(String anOrderStatusUrl) {
		this.orderStatusUrl = anOrderStatusUrl;
	}

	/**
	 * This method sets the <samp>postback</samp> URL.
	 *
	 * @param aPostbackUrl
	 *            The URL to use to post back.
	 */
	public void setPostbackUrl(String aPostbackUrl) {
		this.postbackUrl = aPostbackUrl;
	}

	/**
	 * This method sets the protocol ID.
	 *
	 * @param aProtocol
	 *            The requisitioner name.
	 */
	public void setProtocolId(Integer aProtocol) {
		this.protocolId = aProtocol;
	}

	/**
	 * This method sets the requisitioner ID.
	 *
	 * @param aReqId
	 *            The requisitioner ID.
	 */
	public void setReqId(String aReqId) {
		this.reqId = aReqId;
	}

	/**
	 * This method sets the requisitioner name.
	 *
	 * @param aReqName
	 *            The buyer cookie.
	 */
	public void setReqName(String aReqName) {
		this.reqName = aReqName;
	}

	/**
	 * This method sets the session ID.
	 *
	 * @param aSessionId
	 *            The buyer cookie
	 */
	public void setSessionId(String aSessionId) {
		this.sessionId = aSessionId;
	}

	/**
	 * This method sets the session information.
	 *
	 * @param aSessionInfo
	 *            The session information.
	 */
	public void setSessionInfo(SessionInfo aSessionInfo) {
		this.sessionInfo = aSessionInfo;

		// set all the properties using sessionInfo
		setReqId(sessionInfo.getReqId());
		setReqName(sessionInfo.getReqName());
		setDeptName(sessionInfo.getDeptName());
		setPostbackUrl(sessionInfo.getPostBackURL());
		setSessionId(sessionInfo.getSessionId());
		setSessionType(sessionInfo.getSessionType());
		setOrderStatusUrl(sessionInfo.getOrderStatusUrl());
		setProtocolId(new Integer(sessionInfo.getProcurementProtocolId()));
	}

	/**
	 * This method sets the session type.
	 * 
	 * @param aSessionType
	 *            The session type.
	 */
	public void setSessionType(String aSessionType) {
		this.sessionType = aSessionType;
	}

	/**
	 * This method sets the supplier ID.
	 *
	 * @param aSupplierId
	 *            The supplier ID.
	 */
	public void setSupplierId(long aSupplierId) {
		this.supplierId = aSupplierId;
	}

	/**
	 * This method sets the user ID.
	 *
	 * @param aUserId
	 *            The user ID.
	 */
	public void setUsersId(Long aUserId) {
		this.userId = aUserId;
	}

	void UpdateSessionInfo() {
	}

	/**
	 * This method checks whether all the required parameters are available for
	 * authentication. To view the required parameters for each authentication
	 * level, see the comments for the class. The required parameters are:
	 * <samp>requisitionerId</samp>, <samp>buyerId</samp>, and
	 * <samp>sessionInfo</samp>. The <samp>requisitionerId</samp> and
	 * <samp>sessionInfo</samp> cannot be null. The <samp>buyerId</samp> cannot
	 * equal "-1".
	 * <p>
	 * 
	 * @exception ECException
	 *                Raised when there is no <code>reqId</code> parameter
	 *                provided to this command.
	 */
	public void validateParameters() throws ECException {
		final String strMethodName = "validateParameters";

		final boolean bTrace = ECTrace.traceEnabled(ECTraceIdentifiers.COMPONENT_USER);
		if (bTrace) {
			Object[] obj = { reqId };
			ECTrace.entry(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, strMethodName, obj);
		}

		if ((reqId == null) || (reqId.trim().length() == 0)) {
			TypedProperty exceptionProperties = new TypedProperty();

			Status s = Status.getStatus(ErrorConstants.REGISTER_REQUISITIONER_FAILED, getCommandContext().getLocale());
			exceptionProperties.put(ECMEUserConstants.STATUS_CODE, "" + s.getCode());
			exceptionProperties.put(ECMEUserConstants.STATUS_TEXT, s.getText());
			exceptionProperties.put(ECMEUserConstants.STATUS_MESSAGE, s.getMessage());
			exceptionProperties.put(ECMEUserConstants.PROCUREMENT_ERROR_CODE,
					(new Integer(ErrorConstants.REGISTER_REQUISITIONER_FAILED)).toString());

			throw new ECApplicationException(ECMEMessage._ERR_PROCUREMENT_INVALID_REQUISITIONER_ID, CLASS_NAME,
					strMethodName, "PunchOutSetupErrorView", exceptionProperties, true);
		}
		if ((buyerId != -1L) && (sessionInfo != null)) {
			checkParametersOk = true;
		}

		if (bTrace) {
			ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, strMethodName);
		}
	}

	/**
	 * Finds the business profile for the business user with the extrinsic user
	 * name.
	 * 
	 * @param aReqId
	 *            the requisitioner id (the extrinsic user name)
	 * @param aBuyerOrgId
	 *            the requisitioner's organization id
	 * @return the business profile access bean
	 * @throws ECSystemException
	 *             Raised when there is any exception encountered calling the
	 *             finder method
	 *             <code>BusinessProfileAccessBean.findByRequisitionerId(String)</code>.
	 */
	private static BusinessProfileAccessBean findBusinessProfile(String aReqId, long aBuyerOrgId)
			throws ECSystemException {
		final String METHODNAME = "findBusinessProfile";
		final boolean bTrace = ECTrace.traceEnabled(ECTraceIdentifiers.COMPONENT_USER);
		boolean isRequisitionerReg = false;
		if (bTrace) {
			ECTrace.entry(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, METHODNAME);
		}

		// Try to find the requisitioner using requisitionerId
		BusinessProfileAccessBean aBean = null;
		try {
			BusinessProfileAccessBean reqAB = new BusinessProfileAccessBean();

			Enumeration e = reqAB.findByRequisitionerId(aReqId);

			if (e.hasMoreElements()) {
				while (e.hasMoreElements() && !isRequisitionerReg) {
					aBean = (BusinessProfileAccessBean) e.nextElement();
					aBean.instantiateEntity();
					Long lOrgId = aBean.getOrganizationIdInEntityType();

					// should be the same as the default buyer id (will still
					// work if there is no
					// orgid provided)
					long orgId = DEFAULT_BUYER_ID;
					if (lOrgId != null) {
						orgId = lOrgId.longValue();
					}
					if (orgId == aBuyerOrgId) {
						isRequisitionerReg = true;
						if (bTrace) {
							ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, METHODNAME,
									"Found a requisitioner with ID = \"" + aReqId + "\" under organization \"" + orgId
											+ "\".");
						}
					}
				}
				if (!isRequisitionerReg && bTrace) {
					ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, METHODNAME,
							"Requisitioner with ID = \"" + aReqId + "\" under organization \"" + aBuyerOrgId
									+ "\" cannot be found.");
				}
			}

		} catch (NoResultException e) {
			isRequisitionerReg = false;
			if (bTrace) {
				ECTrace.trace(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, METHODNAME,
						"Requisitioner with ID = \"" + aReqId + "\" cannot be found.");
			}
		}

		if (bTrace) {
			ECTrace.exit(ECTraceIdentifiers.COMPONENT_USER, CLASS_NAME, METHODNAME);
		}
		if (isRequisitionerReg) {
			return aBean;
		}
		return null;
	}

	/**
	 * This method sets the email.
	 *
	 * @param email
	 *            The email ID.
	 */
	public void setEmail(String email) {
		this.email = email;
	}
}