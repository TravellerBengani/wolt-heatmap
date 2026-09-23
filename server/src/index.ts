import "dotenv/config";
import express from "express";
import { stopsRouter } from "./routes/stops.js";
import { heatmapRouter } from "./routes/heatmap.js";

const app = express();
app.use(express.json());

app.use("/v1/stops", stopsRouter);
app.use("/v1/heatmap", heatmapRouter);

const port = process.env.PORT ?? 3000;
app.listen(port, () => {
  console.log(`wolt-heatmap server listening on :${port}`);
});
