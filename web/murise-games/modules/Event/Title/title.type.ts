export type AnswerScope = "film" | "show" | "stage_play" | "franchise";

export interface TeamTitleResponse {
  teamId: string;
  teamName: string;
  teamColor: string;
  title: string | null;        // null until the host registers it
  answerScope: AnswerScope | null;
  aliases: string[];
}

export interface TitleBoardResponse {
  teams: TeamTitleResponse[];
}

export interface TitleSaveRequest {
  title: string;
  answerScope: AnswerScope;
  aliases: string[];
}