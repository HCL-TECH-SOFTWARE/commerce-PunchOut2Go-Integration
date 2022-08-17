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
const { createProxyMiddleware } = require("http-proxy-middleware");

// update this point to your Search and Transaction server.
// const SEARCH_HOST = MOCK_HOST;
// CHANGE SEARCH_HOST to point to the Docker Search Query Service for ElasticSearch
// for example: const SEARCH_HOST = "https://10.190.66.159:30901";

const SEARCH_HOST = "https://localhost:30901";
const TRANSACTION_HOST = "https://localhost";
const MOCK_HOST = "http://localhost:9002";
const CMC_HOST = "https://10.115.171.46:7443";
const DX_HOST = "https://10.115.171.46";

/* PUNCHOUT2GO START */
const PUNCHOUT2GO_HOST = "https://connect.punchout2go.com";
/* PUNCHOUT2GO END */

const useMock = () => {
  return process.env.REACT_APP_MOCK === "true";
};

const appHost = `${process.env.HTTPS === "true" ? "https" : "http"}://localhost:${
  process.env.PORT ? process.env.PORT : 3000
}`;

let searchTerm = "";
const minPrice = "100";
const maxPrice = "500";

const AppName = process.env.REACT_APP_STORENAME;

if (AppName === "Emerald") {
  searchTerm = "bed";
} else {
  searchTerm = "bolt";
}

const mockPathRewrite = (path, req) => {
  let newPath = path.replace(/searchTerm=[a-zA-Z0-9]+/, "searchTerm=" + searchTerm);
  if (newPath.indexOf("minPrice=0&") === -1 || newPath.indexOf("maxPrice=500&") === -1) {
    newPath = newPath.replace(/minPrice=\d+/, "minPrice=" + minPrice);
    newPath = newPath.replace(/maxPrice=\d+/, "maxPrice=" + maxPrice);
  }
  return newPath;
};

const storeAssetPathRewrite = {
  "^/EmeraldSAS": `/${AppName}/EmeraldSAS`,
  "^/EmeraldCAS": `/${AppName}/EmeraldCAS`,
  "^/SapphireSAS": `/${AppName}/SapphireSAS`,
  "^/SapphireCAS": `/${AppName}/SapphireCAS`,
  "^/hclstore": `/${AppName}/`,
  "^/wcsstore": `/${AppName}/`,
  "^/ExtendedSitesCatalogAssetStore": `/${AppName}/ExtendedSitesCatalogAssetStore`,
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
const dxProxyContext = {
  target: DX_HOST,
  ...options,
};
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
  app.use(["/search/resources/api/", "/search/resources/store/"], createProxyMiddleware(searchProxyContext));
  app.use("/wcs/resources/", createProxyMiddleware(transactionProxyContext));
  app.use(
    [
      "/hclstore",
      "/wcsstore",
      "/EmeraldSAS",
      "/EmeraldCAS",
      "/SapphireSAS",
      "/EmeraldPlusSAS",
      "/SapphirePlusSAS",
      "/ExtendedSitesCatalogAssetStore",
    ],
    createProxyMiddleware(hclStoreAssetProxyContext)
  );
  app.use(["/lobtools/", "/tooling/", "/sockjs-node/", "/rest/"], createProxyMiddleware(lobToolsProxyContext));
  app.use("/dx/", createProxyMiddleware(dxProxyContext));
   /* PUNCHOUT2GO START */
   app.use("/gateway/link/api/", createProxyMiddleware(punchOut2GoApiProxyContext));
   /* PUNCHOUT2GO END */
};
