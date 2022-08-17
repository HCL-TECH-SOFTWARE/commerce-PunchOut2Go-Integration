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


INSERT INTO PROCSYS (PROCSYSNAME) VALUES ('PO2GO');

INSERT INTO WCS.PROCPROTCL (PROCPROTCL_ID, PROCSYSNAME, PROTOCOLNAME, VERSION, AUTHTYPE, TWOSTEPMODE, CLASSIFDOMAIN, OPTCOUNTER)
VALUES(1, 'PO2GO', 'JSON-WSP', '1.0', 3, 'Y', 'UNSPSC', 1);

INSERT INTO BUYSUPMAP (SUPORG_ID, BUYORGUNIT_ID, CATALOG_ID, PROCPROTCL_ID, CONTRACT_ID, MBRGRP_ID) 
VALUES (<SUPORG_ID>, <BUYORG_ID>, <CATALOG_ID>, 1, <CONTRACT_ID>, -132);


INSERT INTO PROCBUYPRF (PROCPROTCL_ID, ORGENTITY_ID, REQIDPARM, ORGUNITPARM) 
VALUES (1, <BUYORG_ID>, 'reqId', '');

INSERT INTO ORGCODE (ORGCODE_ID, ORGENTITY_ID, CODETYPE, CODE) VALUES (1, <SUPORG_ID>, 'DUNS', '<SUPORG_NAME>'); 
INSERT INTO ORGCODE (ORGCODE_ID, ORGENTITY_ID, CODETYPE, CODE) VALUES (2, <BUYORG_ID>, 'DUNS', '<BUYORG_NAME>');

-- CMDREG 

INSERT INTO CMDREG (STOREENT_ID, INTERFACENAME, CLASSNAME, TARGET, OPTCOUNTER) 
	VALUES (<store_id>, 'com.ibm.commerce.me.commands.PunchOutSetupCmd', 'com.hcl.commerce.integration.punchout.commands.PunchOutSetupExtCmdImpl', 'Local', 1);
INSERT INTO CMDREG (STOREENT_ID, INTERFACENAME, CLASSNAME, TARGET, OPTCOUNTER) 
	VALUES (<store_id>, 'com.ibm.commerce.me.commands.RegisterRequisitionerCmd', 'com.hcl.commerce.integration.punchout.commands.RegisterRequisitionerExtCmdImpl', 'Local', 1);
INSERT INTO CMDREG (STOREENT_ID, INTERFACENAME, CLASSNAME, TARGET, OPTCOUNTER) 
	VALUES (<store_id>, 'com.ibm.commerce.me.commands.SubmitShoppingCartCmd', 'com.hcl.commerce.integration.punchout.commands.SubmitShoppingCartExtCmdImpl', 'Local', 1);
INSERT INTO CMDREG (STOREENT_ID, INTERFACENAME, CLASSNAME, TARGET, OPTCOUNTER) 
	VALUES (<store_id>, 'com.ibm.commerce.me.commands.BatchOrderRequestCmd', 'com.hcl.commerce.integration.punchout.commands.BatchOrderRequestExtCmdImpl', 'Local', 1);
	
-- STORCONF

INSERT INTO STORECONF (STOREENT_ID, NAME, VALUE, OPTCOUNTER) VALUES (<store_id>, 'Store_SignIn_URL', '<Store_SignIn_URL>', 0);

-- Use Default Password for Requisitioning user. Use wcs_encrypt.bat to encrypt the default password and replace it in value. (Here we have "Passw0rd" as default password)
INSERT INTO STORECONF (STOREENT_ID, NAME, VALUE, OPTCOUNTER) VALUES (<store_id>, 'Requisitioner_User_Default_Password', 'xXt6j94wApznLeFI/oNZSso373gtZtA9MJ/gx+37mDk=', 0);

COMMIT;