import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ChatSummary, ChatDetail } from '../models/chat.model';

@Injectable({ providedIn: 'root' })
export class ChatService {
  private baseUrl = '/api/chats';

  constructor(private http: HttpClient) {}

  listChats(): Observable<ChatSummary[]> {
    return this.http.get<ChatSummary[]>(this.baseUrl);
  }

  getChat(id: number): Observable<ChatDetail> {
    return this.http.get<ChatDetail>(`${this.baseUrl}/${id}`);
  }

  createChat(): Observable<ChatDetail> {
    return this.http.post<ChatDetail>(this.baseUrl, null);
  }

  streamAddMessage(chatId: number, content: string): Observable<string> {
    return new Observable(subscriber => {
      const controller = new AbortController();

      fetch(`${this.baseUrl}/${chatId}/messages`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content }),
        signal: controller.signal,
      }).then(async response => {
        const reader = response.body!.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });

          const parts = buffer.split('\n\n');
          buffer = parts.pop()!;

          for (const part of parts) {
            const dataLine = part.split('\n').find(l => l.startsWith('data:'));
            if (dataLine) subscriber.next(dataLine.slice(5));
          }
        }
        subscriber.complete();
      }).catch(err => {
        if (err.name !== 'AbortError') subscriber.error(err);
      });

      return () => controller.abort();
    });
  }
}
