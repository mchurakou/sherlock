import { Component, NgZone, OnInit } from '@angular/core';
import { ChatList } from './components/chat-list/chat-list';
import { ChatView } from './components/chat-view/chat-view';
import { ChatService } from './services/chat.service';
import { ChatSummary, ChatDetail, MessageInput } from './models/chat.model';

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

  onMessageSent(input: MessageInput) {
    if (this.selectedChat) {
      this.streamMessage(this.selectedChat.id, input);
    } else {
      this.chatService.createChat().subscribe(chat => {
        this.selectedChatId = chat.id;
        this.selectedChat = chat;
        this.loadChats();
        this.streamMessage(chat.id, input);
      });
    }
  }

  private streamMessage(chatId: number, input: MessageInput) {
    if (this.selectedChat) {
      this.selectedChat = {
        ...this.selectedChat,
        messages: [...this.selectedChat.messages, {
          id: -1, content: input.content, role: 'USER', createdAt: new Date().toISOString(),
          imageData: input.imageBase64, imageMimeType: input.imageMimeType,
        }],
      };
    }
    this.streamingContent = '';

    this.chatService.streamAddMessage(chatId, input).subscribe({
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
