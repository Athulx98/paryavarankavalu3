service cloud.firestore {
  match /databases/{database}/documents {
    // Allow users to read/write their own profile
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Allow authenticated users to read and create reports
    match /reports/{reportId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null;
      // Allow update if the user is the reporter or the assigned cleaner
      allow update: if request.auth != null; 
    }
  }
}