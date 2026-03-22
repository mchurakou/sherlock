import { Component, ElementRef, EventEmitter, Input, Output, ViewChild, AfterViewChecked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChatDetail, MessageInput } from '../../models/chat.model';
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
  @Output() messageSent = new EventEmitter<MessageInput>();

  @ViewChild('messagesContainer') private messagesContainer!: ElementRef;
  @ViewChild('fileInput') private fileInput!: ElementRef<HTMLInputElement>;

  newMessage = '';
  selectedImageBase64: string | undefined;
  selectedImageMimeType: string | undefined;
  selectedImagePreview: string | undefined;
  private shouldScroll = false;

  get isStreaming(): boolean {
    return this.streamingContent !== null;
  }

  sendMessage() {
    const content = this.newMessage.trim();
    if ((content || this.selectedImageBase64) && !this.isStreaming) {
      this.messageSent.emit({
        content,
        imageBase64: this.selectedImageBase64,
        imageMimeType: this.selectedImageMimeType,
      });
      this.newMessage = '';
      this.clearImage();
      this.shouldScroll = true;
    }
  }

  onFileSelected(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const dataUrl = reader.result as string;
      const comma = dataUrl.indexOf(',');
      this.selectedImageBase64 = dataUrl.slice(comma + 1);
      this.selectedImageMimeType = file.type;
      this.selectedImagePreview = dataUrl;
    };
    reader.readAsDataURL(file);
  }

  clearImage() {
    this.selectedImageBase64 = undefined;
    this.selectedImageMimeType = undefined;
    this.selectedImagePreview = undefined;
    if (this.fileInput?.nativeElement) {
      this.fileInput.nativeElement.value = '';
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
