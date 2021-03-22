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

const { createProxyMiddleware } = require("http-proxy-middleware");

// update this point to your Search and Transaction server.
// const SEARCH_HOST = MOCK_HOST;
// CHANGE SEARCH_HOST to point to the Docker Search Query Service for ElasticSearch
// for example const SEARCH_HOST = "https://10.190.66.159:30901";
const SEARCH_HOST = "https://10.0.0.4:30921";
const TRANSACTION_HOST = "https://localhost";
const MOCK_HOST = "http://localhost:9002";
const CMC_HOST = "https://localhost:7443";

/* HCLDAM START */
const DX_HOST = "http://localhost:3000";
/* HCLDAM END */

/* UNICA START */
const UNICA_HOST = "http://185.64.246.214:7001";
/* UNICA END */

/* PUNCHOUT2GO START */
const PUNCHOUT2GO_HOST = "https://connect.punchout2go.com";
/* PUNCHOUT2GO END */

const useMock = () => {
  return process.env.REACT_APP_MOCK === "true";
};

const appHost = `${
  process.env.HTTPS === "true" ? "https" : "http"
}://localhost:${process.env.PORT ? process.env.PORT : 3000}`;

const mockPathRewrite = (path, req) => {
  let newPath = path.replace(/storeId=\d+/, "storeId=12101");
  newPath = newPath.replace(/catalogId=\d+/, "catalogId=11051");
  newPath = newPath.replace(/searchTerm=[a-zA-Z0-9]+/, "searchTerm=bed");
  newPath = newPath.replace(/term=[a-zA-Z0-9]+/, "term=bed");
  newPath = newPath.replace(
    /contractId=[-]*\d+/,
    "contractId=4000000000000000503"
  );
  newPath = newPath.replace(
    /activeOrgId=[-]*\d+/,
    "activeOrgId=7000000000000003002"
  );
  newPath = newPath.replace(/orderId=\d+/, "orderId=mockOrderId");
  newPath = newPath.replace(/pageSize=\d+/, "pageSize=5");
  newPath = newPath.replace(/pageNumber=\d+/, "pageNumber=1");
  if (
    newPath.indexOf("minPrice=0&") === -1 ||
    newPath.indexOf("maxPrice=500&") === -1
  ) {
    newPath = newPath.replace(/minPrice=\d+/, "minPrice=0");
    newPath = newPath.replace(/maxPrice=\d+/, "maxPrice=0");
  }
  return newPath;
};

const storeAssetPathRewrite = (path, req) => {
  return path.replace("/hclstore/", "/").replace("/wcsstore/", "/");
};

const options = {
  changeOrigin: true,
  secure: false,
};

const searchProxyContext = useMock()
  ? {
      target: MOCK_HOST,
      ...options,
      pathRewrite: mockPathRewrite,
    }
  : {
      target: SEARCH_HOST,
      ...options,
    };

const hclStoreAssetProxyContext = {
  target: appHost,
  ...options,
  pathRewrite: storeAssetPathRewrite,
};

const lobToolsProxyContext = {
  target: CMC_HOST,
  ...options,
};

const transactionProxyContext = useMock()
  ? {
      target: MOCK_HOST,
      ...options,
      pathRewrite: mockPathRewrite,
    }
  : {
      target: TRANSACTION_HOST,
      ...options,
    };

/* HCLDAM START */
const hcldxApiProxyContext = useMock()
  ? {
      target: DX_HOST,
      ...options,
    }
  : {
      target: DX_HOST,
      ...options,
    };
/* HCLDAM END */

/* UNICA START */
const hclUnicaApiProxyContext = useMock()
  ? {
      target: UNICA_HOST,
      ...options,
    }
  : {
      target: UNICA_HOST,
      ...options,
    };
/* UNICA END */

/* PUNCHOUT2GO START */
const punchOut2GoApiProxyContext = useMock()
  ? {
      target: PUNCHOUT2GO_HOST,
      ...options,
    }
  : {
      target: PUNCHOUT2GO_HOST,
      ...options,
    };
/* PUNCHOUT2GO END */

module.exports = function (app) {
  app.use(
    ["/search/resources/api/", "/search/resources/store/"],
    createProxyMiddleware(searchProxyContext)
  );
  app.use("/wcs/resources/", createProxyMiddleware(transactionProxyContext));
  app.use(
    ["/hclstore/", "/wcsstore/ExtendedSitesCatalogAssetStore"],
    createProxyMiddleware(hclStoreAssetProxyContext)
  );
  app.use(
    ["/lobtools/", "/tooling/", "/sockjs-node/", "/rest/"],
    createProxyMiddleware(lobToolsProxyContext)
  );
  /* HCLDAM START */
  app.use("/dx/", createProxyMiddleware(hcldxApiProxyContext));
  /* HCLDAM END */
  /* UNICA START */
  app.use("/interact/", createProxyMiddleware(hclUnicaApiProxyContext));
  /* UNICA END */
  /* PUNCHOUT2GO START */
  app.use("/gateway/link/api/", createProxyMiddleware(punchOut2GoApiProxyContext));
  /* PUNCHOUT2GO END */
};
