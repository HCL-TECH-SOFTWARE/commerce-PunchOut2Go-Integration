package com.hcl.commerce.integration.punchout.handler;

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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.apache.commons.json.JSONArray;
import org.apache.commons.json.JSONException;
import org.apache.commons.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.apache.wink.common.http.HttpStatus;

import com.ibm.commerce.datatype.TypedProperty;
import com.ibm.commerce.exception.ECSystemException;
import com.ibm.commerce.exception.ParameterNotFoundException;
import com.ibm.commerce.foundation.internal.server.services.registry.StoreConfigurationRegistry;
import com.ibm.commerce.foundation.rest.util.CommerceTokenHelper;
import com.ibm.commerce.member.facade.client.PersonException;
import com.ibm.commerce.rest.classic.core.AbstractConfigBasedClassicHandler;
import com.ibm.commerce.rest.javadoc.ResponseSchema;
import com.ibm.commerce.rest.member.handler.LoginIdentityHandler;
import com.ibm.commerce.rest.utils.PersonalizationIdHelper;
import com.ibm.commerce.util.wrapper.AES128Cryptx;

/**
 * This Rest Handler is used for Session Setup, Submit Shopping Cart for
 * Approval to Procurement System and Submit the Order from Procuremnet System
 * to HCL Commerce.
 */
@Path("punchout/{storeId}")
public class PunchOutRestHandler extends AbstractConfigBasedClassicHandler {
	private static final String CLASSNAME = PunchOutRestHandler.class.getName();
	private static final String RESOURCE_NAME = "punchout";
	private static final String SESSION_SETUP_REQUEST = "session/request";
	private static final String ORDER_SUBMIT = "orderSubmit";
	private static final String SUBMIT_SHOPPING_CART = "submitShoppingCart";
	private static final String STORE_SIGNIN_URL = "Store_SignIn_URL";

	public String store_Id;

	private static final String CLASS_NAME_PARAMETER_SESSION_SETUP_REQUEST = "com.ibm.commerce.me.commands.PunchOutSetupCmd";
	private static final String CLASS_NAME_PARAMETER_ORDER_SUBMIT = "com.ibm.commerce.me.commands.BatchOrderRequestCmd";
	private static final String CLASS_NAME_PARAMETER_SUBMIT_SHOPPING_CART = "com.ibm.commerce.me.commands.SubmitShoppingCartCmd";

	@Override
	public String getResourceName() {
		return RESOURCE_NAME;
	}

	/**
	 * To serve the Session Setup Request coming from Procurement System via
	 * PunchOut2Go
	 * 
	 * @param storeId
	 * @param responseFormat
	 * @return Response
	 */
	@Path(SESSION_SETUP_REQUEST)
	@POST
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.APPLICATION_XHTML_XML,
			MediaType.APPLICATION_ATOM_XML })
	@ResponseSchema(parameterGroup = RESOURCE_NAME, responseCodes = {
			@com.ibm.commerce.rest.javadoc.ResponseCode(code = 200, reason = "The requested completed successfully."),
			@com.ibm.commerce.rest.javadoc.ResponseCode(code = 400, reason = "Bad request. Some of the inputs provided to the request aren't valid."),
			@com.ibm.commerce.rest.javadoc.ResponseCode(code = 401, reason = "Not authenticated. The user session isn't valid."),
			@com.ibm.commerce.rest.javadoc.ResponseCode(code = 403, reason = "The user isn't authorized to perform the specified request."),
			@com.ibm.commerce.rest.javadoc.ResponseCode(code = 404, reason = "The specified resource couldn't be found."),
			@com.ibm.commerce.rest.javadoc.ResponseCode(code = 500, reason = "Internal server error. Additional details will be contained on the server logs.") })
	public Response sessionSetUpRequest(@PathParam("storeId") String storeId,
			@QueryParam(value = "responseFormat") String responseFormat) throws Exception {

		String METHODNAME = "sessionSetUpRequest";
		Response response = null;
		try {
			TypedProperty requestProperties = initializeRequestPropertiesFromRequestMap(responseFormat);
			store_Id = storeId;
			if (responseFormat == null)
				responseFormat = "application/json";
			response = executeControllerCommandWithContext(storeId, CLASS_NAME_PARAMETER_SESSION_SETUP_REQUEST,
					requestProperties, responseFormat);

			MultivaluedMap<String, Object> map = response.getMetadata();

			if (response.getStatus() == HttpStatus.OK.getCode()) {
				Map<String, Object> responseData = new HashMap<>();
				Map<String, Object> inputData = new HashMap<>();
				inputData.put("logonId", requestProperties.get("reqId"));
				
				StoreConfigurationRegistry storeConfRegistry = StoreConfigurationRegistry.getSingleton();
				String reqUserDefaultPasswordEncrypted = storeConfRegistry.getValue(Integer.parseInt(storeId), "Requisitioner_User_Default_Password");
				
				inputData.put("logonPassword", AES128Cryptx.decrypt(reqUserDefaultPasswordEncrypted, null));

				Map<String, Object> loginResponseData = loginUser(inputData);
				responseData.put("startURL", getStoreUrlWithAuthTokens(loginResponseData, requestProperties));

				response = generateResponseFromHttpStatusCodeAndRespData(responseFormat, responseData, HttpStatus.OK);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return response;
	}

	/**
	 * Create the Store URL with Auth tokens (WCToken + WCTrustedToken) for auto
	 * login
	 * 
	 * @param inputData
	 * @param requestProperties
	 * @return Object
	 */
	private Object getStoreUrlWithAuthTokens(Map<String, Object> inputData, TypedProperty requestProperties)
			throws ParameterNotFoundException {
		StringBuilder storeUrlWithAuthTokens = new StringBuilder();
		StoreConfigurationRegistry storeConfRegistry = StoreConfigurationRegistry.getSingleton();
		String storeURL = storeConfRegistry.getValue(Integer.valueOf(Integer.parseInt(store_Id)), STORE_SIGNIN_URL);
		storeUrlWithAuthTokens.append(storeURL + "?");
		storeUrlWithAuthTokens.append("token=" + inputData.get("WCToken"));
		storeUrlWithAuthTokens.append("&secret=" + inputData.get("WCTrustedToken"));
		storeUrlWithAuthTokens.append("&userId=" + inputData.get("userId"));
		storeUrlWithAuthTokens.append("&personalizationID=" + inputData.get("personalizationID"));
		storeUrlWithAuthTokens.append("&source=punchOut2Go");

		String returnUrl = requestProperties.getString("return_url");
		String redirectUrl = requestProperties.getString("redirect_url");
		if (StringUtils.isNotEmpty(returnUrl))
			storeUrlWithAuthTokens.append("&returnParam=" + returnUrl.substring(returnUrl.lastIndexOf('/') + 1));
		if (StringUtils.isNotEmpty(redirectUrl))
			storeUrlWithAuthTokens.append("&redirectParam="
					+ redirectUrl.substring(redirectUrl.lastIndexOf('/') + 1, redirectUrl.lastIndexOf('?')));

		return storeUrlWithAuthTokens.toString();
	}

	/**
	 * Login the requisitioning user and return the map of Auth tokens, userId
	 * and personalizationID
	 * 
	 * @param inputData
	 * @return map of Auth tokens, userId and personalizationID
	 */
	private Map<String, Object> loginUser(Map<String, Object> inputData) throws PersonException, ECSystemException {
		LoginIdentityHandler loginIdentityHandler = new LoginIdentityHandler();
		Map<String, Object> responseData = new HashMap<>();

		Map authInfo = loginIdentityHandler.loginUser(businessContext, activityTokenCallbackHandler, inputData);

		String[] userIds = (String[]) authInfo.get("userId");
		responseData.put("userId", userIds[0]);

		Long userId = (userIds[0] != null) && (userIds[0].trim().length() > 0) ? Long.valueOf(userIds[0]) : null;
		String personalizationId = PersonalizationIdHelper.getPersonalizationId(userId);
		if (personalizationId != null) {
			responseData.put("personalizationID", personalizationId);
		}

		Map commerceTokens = CommerceTokenHelper.generateCommerceTokens(authInfo);
		responseData.put("WCToken", commerceTokens.get("WCToken"));
		responseData.put("WCTrustedToken", commerceTokens.get("WCTrustedToken"));

		return responseData;
	}

	/**
	 * To serve the Submit Shopping Cart Request. Change the Order Status from
	 * pending (P) to waiting approval (W)
	 * 
	 * @param storeId
	 * @param responseFormat
	 * @return Response
	 */
	@Path(SUBMIT_SHOPPING_CART)
	@POST
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.APPLICATION_XHTML_XML,
			MediaType.APPLICATION_ATOM_XML })
	@ResponseSchema(parameterGroup = RESOURCE_NAME, responseCodes = {
			@com.ibm.commerce.rest.javadoc.ResponseCode(code = 200, reason = "The requested completed successfully."),
			@com.ibm.commerce.rest.javadoc.ResponseCode(code = 400, reason = "Bad request. Some of the inputs provided to the request aren't valid."),
			@com.ibm.commerce.rest.javadoc.ResponseCode(code = 401, reason = "Not authenticated. The user session isn't valid."),
			@com.ibm.commerce.rest.javadoc.ResponseCode(code = 403, reason = "The user isn't authorized to perform the specified request."),
			@com.ibm.commerce.rest.javadoc.ResponseCode(code = 404, reason = "The specified resource couldn't be found."),
			@com.ibm.commerce.rest.javadoc.ResponseCode(code = 500, reason = "Internal server error. Additional details will be contained on the server logs.") })
	public Response submitShoppingCart(@PathParam("storeId") String storeId,
			@QueryParam(value = "responseFormat") String responseFormat) throws Exception {
		String METHODNAME = "submitShoppingCart";
		Response response = null;
		try {
			TypedProperty requestProperties = initializeRequestPropertiesFromRequestMap(responseFormat);

			store_Id = storeId;
			if (responseFormat == null)
				responseFormat = "application/json";
			response = executeControllerCommandWithContext(storeId, CLASS_NAME_PARAMETER_SUBMIT_SHOPPING_CART,
					requestProperties, responseFormat);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return response;
	}

	/**
	 * To serve the Order Submit Request coming from Procurement System via
	 * PunchOut2Go
	 * 
	 * @param storeId
	 * @param responseFormat
	 * @return Response
	 */
	@Path(ORDER_SUBMIT)
	@POST
	@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.APPLICATION_XHTML_XML,
			MediaType.APPLICATION_ATOM_XML })
	@ResponseSchema(parameterGroup = RESOURCE_NAME, responseCodes = {
			@com.ibm.commerce.rest.javadoc.ResponseCode(code = 200, reason = "The requested completed successfully."),
			@com.ibm.commerce.rest.javadoc.ResponseCode(code = 400, reason = "Bad request. Some of the inputs provided to the request aren't valid."),
			@com.ibm.commerce.rest.javadoc.ResponseCode(code = 401, reason = "Not authenticated. The user session isn't valid."),
			@com.ibm.commerce.rest.javadoc.ResponseCode(code = 403, reason = "The user isn't authorized to perform the specified request."),
			@com.ibm.commerce.rest.javadoc.ResponseCode(code = 404, reason = "The specified resource couldn't be found."),
			@com.ibm.commerce.rest.javadoc.ResponseCode(code = 500, reason = "Internal server error. Additional details will be contained on the server logs.") })
	public Response orderSubmitRequest(@PathParam("storeId") String storeId,
			@QueryParam(value = "responseFormat") String responseFormat) throws Exception {
		String METHODNAME = "orderSubmit";
		Response response = null;
		try {
			TypedProperty requestProperties = initializeRequestPropertiesFromRequestMap(responseFormat);
			convertJSONArrayToTypeProperty(requestProperties);

			store_Id = storeId;
			if (responseFormat == null)
				responseFormat = "application/json";
			response = executeControllerCommandWithContext(storeId, CLASS_NAME_PARAMETER_ORDER_SUBMIT,
					requestProperties, responseFormat);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return response;
	}

	/**
	 * Convert the JSON Array to TypeProperty
	 * 
	 * @param input typeProperty
	 */
	private void convertJSONArrayToTypeProperty(TypedProperty input) throws ParameterNotFoundException, JSONException {
		Set<?> inputSetKeys = input.keySet();
		Iterator<?> inputKeys = inputSetKeys.iterator();
		while (inputKeys.hasNext()) {
			String inputKey = (String) inputKeys.next();
			Object inputValues = input.get(inputKey);

			if (inputValues instanceof List) {
				Vector vector = new Vector();

				JSONArray jsonArray = (JSONArray) input.get(inputKey);
				for (int i = 0; i < jsonArray.size(); i++) {
					JSONObject tempJSON = jsonArray.getJSONObject(i);
					TypedProperty tempProperty = new TypedProperty();
					Iterator<?> keys = tempJSON.keys();

					while (keys.hasNext()) {
						String key = (String) keys.next();
						Object value = tempJSON.get(key);
						tempProperty.put(key, value);
						if (value instanceof List) {
							convertJSONArrayToTypeProperty(tempProperty);
						}
					}

					vector.add(tempProperty);
				}

				input.put(inputKey, vector);
			}
		}
	}
}
