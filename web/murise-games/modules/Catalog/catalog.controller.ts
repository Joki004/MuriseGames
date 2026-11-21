import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { CatalogService } from "./catalog.service";
import type {
  CatalogGameCreateRequest,
  CatalogGameResponse,
} from "./catalog.type";

export const CatalogController = {
  async list(): Promise<CatalogGameResponse[]> {
    try {
      const response = await CatalogService.list();
      return response.data ?? [];
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not load game catalog.")
      );
    }
  },

  async create(payload: CatalogGameCreateRequest): Promise<CatalogGameResponse> {
    try {
      const response = await CatalogService.create(payload);
      if (!response.data) throw new Error("Empty catalog game response.");
      return response.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not create catalog game.")
      );
    }
  },
};