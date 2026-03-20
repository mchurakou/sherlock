import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ChatSummary } from '../../models/chat.model';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-chat-list',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './chat-list.html',
  styleUrl: './chat-list.css'
})
export class ChatList {
  @Input() chats: ChatSummary[] = [];
  @Input() selectedChatId: number | null = null;
  @Output() chatSelected = new EventEmitter<number>();
  @Output() newChat = new EventEmitter<void>();

  selectChat(id: number) {
    this.chatSelected.emit(id);
  }

  onNewChat() {
    this.newChat.emit();
  }
}
