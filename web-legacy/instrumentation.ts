export async function register() {
  if (process.env.NEXT_RUNTIME === "nodejs") {
    const { default: logger } = await import("./src/lib/logger");

    process.on("uncaughtException", (err) => {
      logger.error({ err }, "Uncaught exception");
    });

    process.on("unhandledRejection", (reason) => {
      logger.error({ reason }, "Unhandled promise rejection");
    });

    logger.info("Next.js server started");
  }
}
