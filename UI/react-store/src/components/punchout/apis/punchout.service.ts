import Axios, { AxiosPromise, AxiosRequestConfig, Canceler } from "axios";
import { request } from "http";
import loginIdentityService from "../../../_foundation/apis/transaction/loginIdentity.service";
import { getSite } from "../../../_foundation/hooks/useSite";
import { storageSessionHandler, windowRegistryHandler } from "../../../_foundation/utils/storageUtil";

/**local URLs (will delete once above will start working) */

const TRANSFER_CART_URL = "https://connect.punchout2go.com/gateway/link/api/id/";
const BACK_TO_PROCUREMENT_URL = "https://connect.punchout2go.com/gateway/link/return/id/";
const CancelToken = Axios.CancelToken;
const cancels: Canceler[] = [];
const payloadBase: any = {
    cancelToken: new CancelToken(function executor(c) {
      cancels.push(c);
    }),
  };
  const payload = {
    ...payloadBase,
  };

const punchoutService = {

submitCart(body): AxiosPromise<any> {
    const storeID = getSite()?.storeID;
    const currentUser = storageSessionHandler.getCurrentUserAndLoadAccount();
    const requestOptions: AxiosRequestConfig = Object.assign({
        url: "/wcs/resources/punchout/" + storeID + "/submitShoppingCart",
        method: "POST",
        data: body,
        headers: {
            "WCToken": currentUser.WCToken,
            "WCTrustedToken": currentUser.WCTrustedToken
        }

    });
    return Axios(requestOptions);
},
    transferCart(requestBody): AxiosPromise<any> {
        const requestOptions: AxiosRequestConfig = Object.assign({
        data: { requestBody },
        url: TRANSFER_CART_URL+sessionStorage.getItem("PUNCHOUT_TRANSFER_CART_PARAM"),
        method: "post"
        });
        return Axios(requestOptions);
    },
    backToProcurement(redirectURL){
        let url ='';
        if(redirectURL){
            url=redirectURL
        }else{
            url = BACK_TO_PROCUREMENT_URL+sessionStorage.getItem('PUNCHOUT_BACK_TO_PROCUREMENT_PARAM')+'?redirect=1';
            console.log(url);
        }
        window.location.replace(url); 

    },
    logoutUserAndRedirect(redirectURL){
        //clear session storage before logging out

        loginIdentityService.logout(payload).then(response=>{

            let url ='';
            if(redirectURL){
                url=redirectURL
            }else{
                url = BACK_TO_PROCUREMENT_URL+sessionStorage.getItem('PUNCHOUT_BACK_TO_PROCUREMENT_PARAM')+'?redirect=1';
                console.log(url);
            }
            sessionStorage.clear();
            window.location.replace(url); 
        }
        ).catch(err=>{
            console.log("Errr while logging out the user")
        })
    }
}

export default punchoutService;
