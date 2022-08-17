/*
 *==================================================
 * Licensed Materials - Property of HCL Technologies
 *
 * HCL Commerce
 *
 * (C) Copyright HCL Technologies Limited 2021
 *
 *==================================================
 */
//Custom libraries

import React from "react";
import { Divider } from "@material-ui/core";
import {
  StyledButton,
  StyledCheckbox,
  StyledChip,
  StyledContainer,
  StyledFormControlLabel,
  StyledGrid,
  StyledInputLabel,
  StyledKeyboardDatePicker,
  StyledMuiPickersUtilsProvider,
  StyledPaper,
  StyledProgressPlaceholder,
  StyledSelect,
  StyledTextField,
  StyledTypography,
  StyledLink,
} from "@hcl-commerce-store-sdk/react-component";
import { useCart } from "../../../_foundation/hooks/use-cart";
import { OrderDiscountSummary } from "../order-discount-summary";
import { OrderItemTable } from "../order-item-table";
import { OrderTotalSummary } from "../order-total-summary";
import { useTranslation } from "react-i18next";
import { CheckoutProfileSelection } from "../checkout-profile-selection";
import DateFnsUtils from "@date-io/date-fns";
import { useNavigate } from "react-router";
import { HOME } from "../../../constants/routes";
import { TransferCart } from "../../punchout/TransferCart";

interface CartLayout {
  isFetching: boolean;
  orderItems: string | any[];
  isRecurringOrderFeatureEnabled: boolean;
  isRecurringOrder: any;
  isRecurringOrderDisabled: any;
  recurringOrderFrequency: any;
  setIsRecurringOrder: any;
  setRecurringOrderFrequency: any;
  hasDiscounts: any;
  DateFnsUtils: any;
  recurringOrderStartDate: any;
  frequencyOptions: any;
  localeMap: any;
  locale: any;
  onDateChange: any;
  onDateError: any;
  promoCode: any;
  resetPromoCodeError: any;
  onPromoCodeChange: any;
  applyPromoCodeBasedOnKey: any;
  promoCodeError: any;
  applyPromoCode: any;
  selectedPromoCodes: any;
  onPromoCodeRemove: any;
  cart: any;
  canContinue: any;
  checkout: any;
  t: any;
  OrderTotalSummary: any;
  OrderItemTable: any;
  OrderDiscountSummary: any;
  checkoutProfile: boolean;
  setCheckoutProfile: any;
  selectedProfile: any;
  setSelectedProfile: any;
  history: any;
}

const CartWidget: React.FC<any> = (props: CartLayout) => {
  //punhout code
  const isPunchout = sessionStorage.getItem('source');
  const { t } = useTranslation();
  const navigate = useNavigate();
  const {
    isFetching,
    orderItems,
    isRecurringOrderFeatureEnabled,
    isRecurringOrder,
    isRecurringOrderDisabled,
    recurringOrderFrequency,
    setIsRecurringOrder,
    setRecurringOrderFrequency,
    hasDiscounts,
    recurringOrderStartDate,
    frequencyOptions,
    localeMap,
    locale,
    onDateChange,
    onDateError,
    promoCode,
    resetPromoCodeError,
    onPromoCodeChange,
    applyPromoCodeBasedOnKey,
    promoCodeError,
    applyPromoCode,
    selectedPromoCodes,
    onPromoCodeRemove,
    cart,
    canContinue,
    checkout,
    selectedProfile,
    setSelectedProfile,
    partitionedBySellers,
  } = useCart();

  const continueShopping = () => {
    navigate(HOME);
  };

  return (
    <StyledContainer className="page">
      <StyledTypography tabIndex="0" variant="h4" component="h1" className="top-margin-2">
        {t("Cart.Title")}
      </StyledTypography>
      <StyledTypography
        variant="body1"
        style={{
          fontWeight: "bold",
          overflow: "hidden",
          overflowWrap: "break-word",
        }}
        className="bottom-padding-2">
        {cart?.orderDescription}
      </StyledTypography>
      {isFetching ? (
        <StyledProgressPlaceholder className="vertical-padding-15" />
      ) : (
        <StyledGrid container spacing={2}>
          {orderItems.length > 0 && isRecurringOrderFeatureEnabled && (
            <StyledGrid item xs={12}>
              <StyledPaper>
                <StyledContainer className="vertical-margin-2">
                  <StyledGrid container spacing={2}>
                    <StyledGrid item xs={12} sm={4} md={3}>
                      <StyledFormControlLabel
                        label={t("Cart.Labels.RecurringOrder")}
                        control={
                          <StyledCheckbox
                            color="secondary"
                            name="recurringOrder"
                            checked={isRecurringOrder}
                            onChange={() => setIsRecurringOrder(!isRecurringOrder)}
                            disabled={isRecurringOrderDisabled}
                          />
                        }
                      />
                    </StyledGrid>
                    {isRecurringOrder && (
                      <>
                        <StyledGrid item xs={8} sm={4} md={2} lg={2}>
                          <StyledInputLabel shrink required id="frequency">
                            {t("Cart.Labels.Frequency")}
                          </StyledInputLabel>
                          <StyledSelect
                            value={recurringOrderFrequency}
                            labelId="frequency"
                            native
                            name="frequency"
                            onChange={(event: { target: { value: any } }) =>
                              setRecurringOrderFrequency(event.target.value)
                            }
                            fullWidth>
                            {frequencyOptions.map((frequency: any, index: number) => (
                              <option value={frequency.value} key={frequency.value}>
                                {t(`${frequency.translationKey}`)}
                              </option>
                            ))}
                          </StyledSelect>
                        </StyledGrid>

                        <StyledGrid item xs={8} sm={4} md={2} lg={2}>
                          <StyledMuiPickersUtilsProvider utils={DateFnsUtils} locale={localeMap[locale]}>
                            <StyledKeyboardDatePicker
                              required
                              disableToolbar
                              disablePast
                              autoOk
                              variant="inline"
                              format="MM/dd/yyyy"
                              label={t("Cart.Labels.StartDate")}
                              value={recurringOrderStartDate}
                              onChange={(date: Date | null) => onDateChange(date)}
                              onError={(error: string) => onDateError(error)}
                              fullWidth
                            />
                          </StyledMuiPickersUtilsProvider>
                        </StyledGrid>
                      </>
                    )}
                  </StyledGrid>
                </StyledContainer>
              </StyledPaper>
            </StyledGrid>
          )}

          <StyledGrid item xs={12}>
            {partitionedBySellers.length ? (
              partitionedBySellers.map((s, key) => (
                <OrderItemTable
                  {...{
                    outerClassName: key > 0 ? "vertical-padding-2 horizontal-padding-2 top-margin-2" : null,
                    key,
                    ...s,
                  }}
                  readOnly={false}
                />
              ))
            ) : orderItems.length > 0 ? (
              <OrderItemTable data={orderItems} readOnly={false} />
            ) : (
              <StyledPaper>
                <StyledContainer className="vertical-margin-2">
                  <StyledTypography variant="body2" component="p">
                    {t("Cart.Msgs.Empty")}
                    <StyledLink to={`${HOME}`}>{t("Cart.Msgs.ShopNow")}</StyledLink>
                  </StyledTypography>
                </StyledContainer>
              </StyledPaper>
            )}
          </StyledGrid>

          {orderItems.length > 0 && (
            <StyledGrid container item justifyContent="flex-end" spacing={2}>
              <StyledGrid item xs={12} sm={6} md={4}>
                <StyledPaper className="vertical-padding-2 horizontal-padding-2">
                  <StyledTypography variant="subtitle1" gutterBottom>
                    {t("Cart.Labels.PromoCode")}
                  </StyledTypography>
                  <StyledTextField
                    type="text"
                    size="small"
                    value={promoCode}
                    id={`cart_input_promocode`}
                    className="bottom-margin-1"
                    onFocus={() => resetPromoCodeError()}
                    onChange={(event: any) => onPromoCodeChange(event)}
                    onKeyDown={(keyPress: any) => applyPromoCodeBasedOnKey(keyPress)}
                    error={promoCodeError}
                    label={t("Cart.Msgs.PromoCode")}
                    fullWidth
                  />
                  <StyledGrid item>
                    <StyledButton
                      testId="cart-apply-promo-code"
                      onClick={(event: any) => applyPromoCode(event)}
                      id={`cart_link_2_promocode`}
                      variant="text">
                      {t("Cart.Actions.Apply")}
                    </StyledButton>
                  </StyledGrid>
                  <StyledGrid container spacing={1}>
                    {selectedPromoCodes.map((promoCode: any, index: number) => (
                      <StyledGrid item xs={12} sm={6} key={promoCode.code}>
                        <StyledChip
                          size="medium"
                          label={promoCode.code}
                          onClick={() => onPromoCodeRemove(promoCode.code)}
                          onDelete={() => onPromoCodeRemove(promoCode.code)}
                          id={`cart_link_3_${index}`}
                        />
                      </StyledGrid>
                    ))}
                  </StyledGrid>
                  {hasDiscounts ? <OrderDiscountSummary order={cart} /> : null}
                </StyledPaper>
              </StyledGrid>
              <StyledGrid item xs={12} sm={6} md={4}>
                <StyledPaper className="vertical-padding-2 horizontal-padding-2">
                  <CheckoutProfileSelection {...{ selectedProfile, setSelectedProfile }} />
                </StyledPaper>
              </StyledGrid>
              <StyledGrid item xs={12} sm={12} md={4}>
                <StyledPaper className="vertical-padding-2 horizontal-padding-2">
                  <StyledTypography variant="subtitle1" gutterBottom>
                    {t("Cart.Labels.OrderSummary")}
                  </StyledTypography>
                  <OrderTotalSummary order={cart} />
                  <Divider className="vertical-margin-2" />
                  <StyledGrid item container direction="row" justifyContent="center" spacing={1}>
                    <StyledGrid item>
                      <StyledButton
                        testId="cart-continue-shop"
                        color="secondary"
                        style={{ maxWidth: "320px" }}
                        onClick={() => continueShopping()}>
                        {t("Cart.Actions.ContinueShopping")}
                      </StyledButton>
                    </StyledGrid>
                    <StyledGrid item>
                    {isPunchout=== 'punchOut2Go'?(<TransferCart cartDetails={cart} orderItems={orderItems}></TransferCart>):
                    (<StyledButton
                        testId={selectedProfile ? "cart-checkout-with-profile" : "cart-checkout"}
                        color="primary"
                        style={{ maxWidth: "320px" }}
                        disabled={!canContinue()}
                        onClick={() => checkout()}>
                        {selectedProfile ? t("Cart.Actions.Checkout-With-Profile") : t("Cart.Actions.Checkout")}
                      </StyledButton>)}
                    </StyledGrid>
                  </StyledGrid>
                </StyledPaper>
              </StyledGrid>
            </StyledGrid>
          )}
        </StyledGrid>
      )}
    </StyledContainer>
  );
};

export default CartWidget;
