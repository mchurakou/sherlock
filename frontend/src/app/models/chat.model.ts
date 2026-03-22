export interface ChatSummary {
  id: number;
  title: string | null;
  createdAt: string;
}

export interface ChatDetail {
  id: number;
  title: string | null;
  createdAt: string;
  messages: Message[];
}

export interface Message {
  id: number;
  content: string;
  role: 'USER' | 'ASSISTANT';
  createdAt: string;
  imageData?: string;
  imageMimeType?: string;
}

export interface MessageInput {
  content: string;
  imageBase64?: string;
  imageMimeType?: string;
}
