import { Component, ElementRef, EventEmitter, Input, Output, ViewChild, AfterViewChecked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChatDetail } from '../../models/chat.model';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-chat-view',
  standalone: true,
  imports: [FormsModule, DatePipe],
  templateUrl: './chat-view.html',
  styleUrl: './chat-view.css'
})
export class ChatView implements AfterViewChecked {
  @Input() chat: ChatDetail | null = null;
  @Input() streamingContent: string | null = null;
  @Output() messageSent = new EventEmitter<string>();

  @ViewChild('messagesContainer') private messagesContainer!: ElementRef;

  newMessage = '';
  private shouldScroll = false;

  get isStreaming(): boolean {
    return this.streamingContent !== null;
  }

  sendMessage() {
    const content = this.newMessage.trim();
    if (content && !this.isStreaming) {
      this.messageSent.emit(content);
      this.newMessage = '';
      this.shouldScroll = true;
    }
  }

  ngAfterViewChecked() {
    if (this.shouldScroll || this.isStreaming) {
      this.scrollToBottom();
      this.shouldScroll = false;
    }
  }

  private scrollToBottom() {
    const el = this.messagesContainer?.nativeElement;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  }
}
