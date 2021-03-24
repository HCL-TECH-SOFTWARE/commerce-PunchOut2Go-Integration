# About the PunchOut2Go Integration Asset

The integration of HCL Commerce with PunchOut2Go where HCL Commerce provides commerce functionality and PunchOut2Go provides two-way integration between an HCL Commerce store and hundreds of e-procurement and ERP platforms. With PunchOut2Go, B2B sellers can provide WebSphere punchout to their customers without the expense of manual integration.

This integration is implemented using REST API approach so that it will be easy to integrate the same with Sapphire (B2B React Store).

# Scope of the Asset:
**•	Session setup:**
1.	Buyer and Supplier Org Authentication
2.	Create the Requisitioner User with default Password
3.	Login the Requisitioner User and create the Auto Login URL with all required tokens

**•	Transfer Cart:**
1. Using autologin URL, login the user to Sapphire
2. User can search and add the products to the cart
3. Transfer the cart data to Punchout2Go and redirect the User to redirect URL provided by Punchout2Go

**•	Order Submission:**
1. Create the Order for items which are approved from procurement system
2. “COD” as Default payment method used

## Out of Scope for this Asset:
•	Commerce will not maintain the Order History it will be taken care by Punchout/Procurement.

## Backend Part
Please refer `Readme.md` file under `Backend folder` to complete the backend task

## UI Part
Please refer `Readme.md` file under `UI folder` to complete the frontend task
