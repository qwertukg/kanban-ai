export type Board = {
    id: number;
    name: string;
    targetBranch: string;
};

export type Column = {
    id: number;
    boardId: number;
    name: string;
    order: number;
    agentId?: number | null;
    system?: boolean;
};

export type Agent = {
    id: number;
    name: string;
    roleInstructions: string;
    acceptanceCriteria: string;
};

export type Task = {
    id: number;
    boardId: number;
    columnId: number;
    title: string;
    description: string;
    branchName?: string;
    status: string;
    messages: { author: string; content: string; timestamp: number }[];
};

export type GitEvent = {
    action: string;
    branch: string;
    target: string;
};
