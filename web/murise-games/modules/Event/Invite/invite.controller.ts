import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { InviteService } from "./invite.service";
import type {
  EventInviteResponse,
  InviteCreateRequest,
  InviteRedeemResponse,
} from "./invite.type";

export interface CreatedInviteLink {
  invite: EventInviteResponse;
  url: string;
}

export const InviteController = {
  async list(eventId: string): Promise<EventInviteResponse[]> {
    try {
      const response = await InviteService.list(eventId);
      if (!response.data) throw new Error("Invite list payload was empty.");
      return response.data;
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not load invites."));
    }
  },

  async create(
    eventId: string,
    input: InviteCreateRequest,
  ): Promise<CreatedInviteLink> {
    if (
      !Number.isInteger(input.expiresInHours) ||
      input.expiresInHours < 1 ||
      input.expiresInHours > 720
    ) {
      throw new Error("Expiry must be between 1 and 720 whole hours.");
    }

    if (
      input.maxUses !== null &&
      (!Number.isInteger(input.maxUses) ||
        input.maxUses < 1 ||
        input.maxUses > 2147483647)
    ) {
      throw new Error("Use limit must be a positive 32-bit integer or blank.");
    }

    try {
      const response = await InviteService.create(eventId, input);
      if (!response.data) throw new Error("Created invite payload was empty.");

      const url = new URL("/join", window.location.origin);
      url.hash = new URLSearchParams({
        token: response.data.token,
      }).toString();

      return {
        invite: response.data.invite,
        url: url.toString(),
      };
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not create invite."),
      );
    }
  },

  async revoke(eventId: string, inviteId: string): Promise<void> {
    try {
      await InviteService.revoke(eventId, inviteId);
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not revoke invite."),
      );
    }
  },

  async redeem(token: string): Promise<InviteRedeemResponse> {
    try {
      const response = await InviteService.redeem(token);

      if (!response.data) {
        throw new Error("Invitation response was empty.");
      }

      return response.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not process the invitation."),
      );
    }
  },
};
