import { ApiService } from "@/modules/Api/api.service";
import type { CustomApiResponse } from "@/modules/Api/api.type";
import type {
  CatalogGameCreateRequest,
  CatalogGameResponse,
} from "./catalog.type";

export const CatalogService = {
  list(): Promise<CustomApiResponse<CatalogGameResponse[]>> {
    return ApiService.get<CatalogGameResponse[]>("/api/catalog/games");
  },

  create(
    payload: CatalogGameCreateRequest
  ): Promise<CustomApiResponse<CatalogGameResponse>> {
    return ApiService.post<CatalogGameResponse>("/api/catalog/games", payload);
  },
};