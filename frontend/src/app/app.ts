import { Component, NgZone, OnInit } from '@angular/core';
import { ChatList } from './components/chat-list/chat-list';
import { ChatView } from './components/chat-view/chat-view';
import { ChatService } from './services/chat.service';
import { ChatSummary, ChatDetail } from './models/chat.model';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [ChatList, ChatView],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  chats: ChatSummary[] = [];
  selectedChat: ChatDetail | null = null;
  selectedChatId: number | null = null;
  streamingContent: string | null = null;

  constructor(private chatService: ChatService, private ngZone: NgZone) {}

  ngOnInit() {
    this.loadChats();
  }

  loadChats() {
    this.chatService.listChats().subscribe(chats => {
      this.chats = chats;
    });
  }

  selectChat(id: number) {
    this.selectedChatId = id;
    this.chatService.getChat(id).subscribe(chat => {
      this.selectedChat = chat;
    });
  }

  startNewChat() {
    this.selectedChat = null;
    this.selectedChatId = null;
  }

  onMessageSent(content: string) {
    if (this.selectedChat) {
      this.streamMessage(this.selectedChat.id, content);
    } else {
      this.chatService.createChat().subscribe(chat => {
        this.selectedChatId = chat.id;
        this.selectedChat = chat;
        this.loadChats();
        this.streamMessage(chat.id, content);
      });
    }
  }

  private streamMessage(chatId: number, content: string) {
    if (this.selectedChat) {
      this.selectedChat = {
        ...this.selectedChat,
        messages: [...this.selectedChat.messages, {
          id: -1, content, role: 'USER', createdAt: new Date().toISOString()
        }],
      };
    }
    this.streamingContent = '';

    this.chatService.streamAddMessage(chatId, content).subscribe({
      next: token => this.ngZone.run(() => {
        this.streamingContent = (this.streamingContent ?? '') + token;
      }),
      complete: () => this.ngZone.run(() => {
        this.chatService.getChat(chatId).subscribe(chat => {
          this.selectedChat = chat;
          this.streamingContent = null;
          this.loadChats();
        });
      }),
      error: () => this.ngZone.run(() => {
        this.streamingContent = null;
      }),
    });
  }
}
