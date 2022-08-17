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

import com.ibm.commerce.command.TaskCommand;
import com.ibm.commerce.me.datatype.SessionInfo;

/**
 * This is the interface of RegisterRequisitionerCmd. It retrieves the
 * requisitioning user if they are already registered, and registers them as a
 * new requisitioning user if they are not registered. The
 * <code>RegisterRequisitioner</code> command is called by the
 * <code>PunchOutSetup</code> and <code>BatchOrderRequest</code> commands after
 * successfully authenticating the <code>PunchOutSetupRequest</code>
 */
public interface RegisterRequisitionerCmd extends TaskCommand {
	public static final String COPYRIGHT = "(c) Copyright International Business Machines Corporation 1996,2008";

	/**
	 * The interface name.
	 */
	public static final String NAME = "com.ibm.commerce.me.commands.RegisterRequisitionerCmd";

	/**
	 * The default implementation class name.
	 */
	public static final String defaultCommandClassName = "com.ibm.commerce.me.commands.RegisterRequisitionerCmdImpl";

	/**
	 * This method gets the userId.
	 *
	 * @return userId The user ID.
	 */
	Long getUsersId();

	/**
	 * This method is called to check whether the registration of the
	 * requisitioner is successful. This method must be called after the command
	 * has been completed.
	 *
	 * @return boolean true if the requisitioner is registered successfully;
	 *         false otherwise
	 */
	public boolean isRegisteredSuccessfully();

	/**
	 * This method sets the buyerId (ID of the buyer organization).
	 *
	 * @param buyerId The buyer organization ID.
	 */
	public void setBuyerId(long buyerId);

	/**
	 * This method sets the department name (if any) to which the requisitioner
	 * belongs to.
	 *
	 * @param deptName
	 *            The department name.
	 */
	void setDeptName(String deptName);

	/**
	 * This method sets the URL for Postback.
	 *
	 * @param postbackUrl
	 *            The postback URL
	 */
	public void setPostbackUrl(String postbackUrl);

	/**
	 * This method sets the protocolId.
	 * 
	 * @param protocol
	 *            The protocol ID.
	 */
	public void setProtocolId(Integer protocol);

	/**
	 * This method sets the requisitioner ID.
	 *
	 * @param reqId
	 *            The requisitioner ID
	 */
	void setReqId(String reqId);

	/**
	 * This method sets the buyer cookie.
	 *
	 * @param sessionId
	 *            The buyer cookie
	 */
	public void setSessionId(String sessionId);

	/**
	 * This method sets the <Samp>sessionInfo</samp>
	 *
	 * @param sessionInfo
	 *            The com.ibm.commerce.me.datatype.SessionInfo.
	 * @see com.ibm.commerce.me.datatype#SessionInfo
	 */
	public void setSessionInfo(SessionInfo sessionInfo);

	/**
	 * This method sets the supplierId.
	 *
	 * @param supplierId
	 *            The supplier ID.
	 */
	public void setSupplierId(long supplierId);

	/**
	 * This method sets the email.
	 *
	 * @param email
	 *            The requisitioner email id.
	 */
	public void setEmail(String email);
}
