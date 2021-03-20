/**
 *==================================================
 * Licensed Materials - Property of HCL Technologies
 *
 * HCL Commerce
 *
 * (C) Copyright HCL Technologies Limited 2020
 *
 *==================================================
 */
//Standard libraries
import React, { useState, useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { useTranslation } from "react-i18next";
import { Redirect } from "react-router-dom";
import queryString from 'query-string'
//Foundation libraries
import { useSite } from "../../../_foundation/hooks/useSite";
//Custom libraries
import { ForgotPassword } from "../forgot-password";
import { HOME } from "../../../constants/routes";
//Redux
import * as userAction from "../../../redux/actions/user";
import { loginStatusSelector } from "../../../redux/selectors/user";
//UI
import {
  StyledTextField,
  StyledButton,
  StyledTypography,
} from "../../StyledUI";
import GoogleSignIn from "../../social-signon/GoogleSignIn";
import FacebookSignIn from "../../social-signon/FacebookSignIn";
import voiceImageTranscibeService from "../../../_foundation/apis/search/voiceImageTranscibe.service";
import GADataService from "../../../_foundation/apis/gtm/gaData.service";
import { FETCH_USER_DETAILS_SUCCESS_ACTION, LOGIN_SUCCESS_ACTION } from "../../../redux/actions/user";
import personService from "../../../_foundation/apis/transaction/person.service";
import { sessionStorageUtil } from "../../../_foundation/utils/storageUtil";
import notificationService from "../../../_foundation/apis/pushNotification/notification.service";

interface SignInContext {
  cid: string;
  redirectRoute?: string;
}

function SignInLayout({ cid, ...props }: SignInContext) {
  const redirectRoute = props.redirectRoute ? props.redirectRoute : HOME;
  const loginStatus = useSelector(loginStatusSelector);
  const { mySite } = useSite();
  const dispatch = useDispatch();
  const { t } = useTranslation();
  const [logonInputProps, setLogonInputProps] = useState<any>({});
  const [email, setEmail] = useState<string>("");
  const [password, setPassword] = useState<string>("");
  const logonIdLabel = t("SignIn.Label.B2B");
  const emailLabel = t("SignIn.Label.Email");

  const handleSubmit = (props: any) => {
    props.preventDefault();
    dispatch(
      userAction.LOGIN_REQUESTED_ACTION({
        body: {
          logonId: email,
          logonPassword: password,
        },
      })
    );
     /**GA360 */
     GADataService.sendFormCompletionEvent(t("SignIn.SignInButton"));
     /**GA360 */
  };

  useEffect(() => {
    if (mySite) {
      if (mySite.isB2B) {
        setLogonInputProps({
          maxLength: 100,
          type: "text",
        });
      } else {
        setLogonInputProps({
          maxLength: 100,
          type: "email",
          placeholder: "name@domain.com",
        });
      }
    }
  }, [mySite]);

  const getQueryStringObject = () => {
    const queryParamsArray = window.location.search.substring(1).split('&');
    let queryStringObject : any = {};

    for (let i = 0; i < queryParamsArray.length; i++) {
      let objArr = queryParamsArray[i].split('=');
      queryStringObject[objArr[0]] = objArr[1];
    }

    return queryStringObject
  }
  

  useEffect(() => {
    const queryStringObject = getQueryStringObject();

    //code for punch out token saving to session storage

    if(queryStringObject.token && queryStringObject.secret) {
      const source = queryStringObject.source === 'punchOut2Go'?'punchOut2Go':'';
      sessionStorage.setItem('source',source);
      sessionStorage.setItem('PUNCHOUT_TRANSFER_CART_PARAM', queryStringObject.returnParam);
      sessionStorage.setItem('PUNCHOUT_BACK_TO_PROCUREMENT_PARAM', queryStringObject.redirectParam);
      console.log(queryStringObject);
      let data = {
        WCToken: queryStringObject.token,
        WCTrustedToken: queryStringObject.secret,
        personalizationID: queryStringObject.personalizationID,
        resourceName: "loginidentity",
        userId: queryStringObject.userId
      }
      dispatch(LOGIN_SUCCESS_ACTION(data));
      personService.findPersonBySelf({}).then(responseObj => {
        dispatch(FETCH_USER_DETAILS_SUCCESS_ACTION(responseObj.data))
      })
    }
  }, []);

  // /**PUSH Notification */
  // const showWelcomeNotification = () => {
  //   if (localStorage.getItem("notification-token")) {
  //     voiceImageTranscibeService.showNotification("Welcome to Emerald","Happy Shopping !!!");
  //   }
  // }
  /**PUSH Notification */
  
  if (loginStatus === true) {
    notificationService.askForNotificationPermission();
    return <Redirect to={redirectRoute} />;
  } else {
    return (
      <>
        <StyledTypography
          component="h1"
          variant="h4"
          className="bottom-margin-1">
          {t("SignIn.ReturningCustomer")}
        </StyledTypography>
        <form onSubmit={handleSubmit}>
          <StyledTextField
            variant="outlined"
            margin="normal"
            required
            fullWidth
            name="email"
            label={mySite.isB2B ? logonIdLabel : emailLabel}
            autoFocus
            onChange={(e) => setEmail(e.target.value)}
            value={email}
            inputProps={logonInputProps}
          />
          <StyledTextField
            variant="outlined"
            margin="normal"
            required
            fullWidth
            name="password"
            label={t("SignIn.Password")}
            onChange={(e) => setPassword(e.target.value)}
            value={password}
            inputProps={{
              maxLength: 100,
              type: "password",
            }}
          />
          <ForgotPassword />
          <StyledButton type="submit" color="primary">
            {t("SignIn.SignInButton")}
          </StyledButton>
        </form>
        <GoogleSignIn/>
        <FacebookSignIn />
      </>
    );
  }
}

export { SignInLayout };
