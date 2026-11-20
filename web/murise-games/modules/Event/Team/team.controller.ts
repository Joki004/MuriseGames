import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { TeamService } from "./team.service";
import type {
  TeamListResponse,
  TeamResponse,
  TeamSaveRequest,
} from "./team.type";

function normalize(input: TeamSaveRequest): TeamSaveRequest {
  const name = input.name.trim();

  if (!name || name.length > 80) {
    throw new Error("Team name must contain between 1 and 80 characters.");
  }

  if (!/^#[0-9a-fA-F]{6}$/.test(input.color)) {
    throw new Error("Choose a valid team color.");
  }

  if (
    !Number.isInteger(input.sortOrder) ||
    input.sortOrder < 0 ||
    input.sortOrder > 2147483647
  ) {
    throw new Error("Display order must be a non-negative 32-bit integer.");
  }

  return { ...input, name };
}

export const TeamController = {
  async list(eventId: string): Promise<TeamListResponse> {
    try {
      const response = await TeamService.list(eventId);
      if (!response.data) throw new Error("Team list payload was empty.");
      return response.data;
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not load teams."));
    }
  },

  async save(
    eventId: string,
    input: TeamSaveRequest,
    teamId?: string,
  ): Promise<TeamResponse> {
    const payload = normalize(input);

    try {
      const response = teamId
        ? await TeamService.update(eventId, teamId, payload)
        : await TeamService.create(eventId, payload);

      if (!response.data) throw new Error("Team payload was empty.");
      return response.data;
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not save the team."));
    }
  },
};