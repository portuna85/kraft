const http = require("node:http");

const request = http.get("http://127.0.0.1:3000", { timeout: 3000 }, (response) => {
  process.exit(response.statusCode && response.statusCode < 500 ? 0 : 1);
});

request.on("timeout", () => {
  request.destroy();
  process.exit(1);
});
request.on("error", () => process.exit(1));
