/*
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
import React from "react";

import { StyledButton } from "../StyledUI";
import { useTranslation } from "react-i18next";
import punchoutService from "./api/punchout.service";
import Axios, { Canceler } from "axios";
import { LOGOUT_REQUESTED_ACTION } from "../../redux/actions/user";
import { useDispatch } from "react-redux";

interface TransferCartProps {
    cartDetails: Object,
    orderItems: Array<[]>
}

/**
 * TransferCart component
 * displays TransferCart button on the cart page in case of punchout
 * @param props
 */
const TransferCart: React.FC<TransferCartProps> = (props: any) => {
    const isPunchout = sessionStorage.getItem('source');
    const dispatch = useDispatch();
    const { t } = useTranslation();
    const cartDetails = props.cartDetails;
    const orderItems = props.orderItems;
    const CancelToken = Axios.CancelToken;
  let cancels: Canceler[] = [];
  const payloadBase: any = {
    cancelToken: new CancelToken(function executor(c) {
      cancels.push(c);
    }),
  };
  const payload = {
    ...payloadBase,
  };
    const handleLogout = (event,url) => {
        event.preventDefault();
        const param: any = {
          payload,
        };
        dispatch(LOGOUT_REQUESTED_ACTION(param));
        punchoutService.backToProcurement(url);
      };

    //transfer cart function for punchOut2Go.
    const transferCartOnClick = (e) => {
        // create the request JSON for punchout
        let requestBody = {};
        var items = [] as any;
        requestBody['total'] = cartDetails.grandTotal;
        const orderId = cartDetails.orderId;
        orderItems.map((item,index)=>{
            let obj ={} as any;
            index++;
            obj['supplierId'] = item.partNumber;
            obj['supplierauxid'] = orderId + '/' + index;
            obj['description'] =item.name;
            obj['classification'] = "";
            obj['unitprice'] = item.unitPrice;
            obj['uom'] = item.UOM;
            obj['quantity'] = item.quantity;
            items.push(obj);
        })
        requestBody['items'] = items;
        //call the submit cart service
        punchoutService.transferCart(requestBody).then((result) => {
            const redirect_url =result.data.redirect_url;
            const submitCartRequestBody={'orderId':cartDetails.orderId};
            punchoutService.submitCart(submitCartRequestBody).then(res=>{
                handleLogout(e,redirect_url);

            }).catch(e=>{
                console.log('Error while submitting the cart')
            });
        }).catch (error => {
        console.log("Error while submitting the cart");
        });
    };

    return (
        <StyledButton
            color="primary"
            className="button"
            fullWidth
            onClick={transferCartOnClick}>
            Transfer Cart
        </StyledButton>
    );
};

export { TransferCart };
