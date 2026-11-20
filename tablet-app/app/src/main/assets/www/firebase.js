
import { initializeApp } from "https://www.gstatic.com/firebasejs/12.4.0/firebase-app.js";
import {
    getAuth,
    onAuthStateChanged,
    signInAnonymously,
    signInWithEmailAndPassword,
    signOut
} from "https://www.gstatic.com/firebasejs/12.4.0/firebase-auth.js";

import {
    getFirestore,
    doc,
    getDoc,
    setDoc,
    updateDoc,
    onSnapshot,
    collection,
    addDoc,
    query,
    where,
    serverTimestamp
} from "https://www.gstatic.com/firebasejs/12.4.0/firebase-firestore.js";

// Firebase connection for Asher's Time Bank
export const firebaseConfig = {
    apiKey: "AIzaSyBnDJuT0p4EOTHk07kgjP56ei8Y46N8vYg",
    authDomain: "asher-s-time-bank.firebaseapp.com",
    projectId: "asher-s-time-bank",
    storageBucket: "asher-s-time-bank.firebasestorage.app",
    messagingSenderId: "647549207508",
    appId: "1:647549207508:web:985645b7c7842e10774c41",
    measurementId: "G-MGL977KN1G"
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

// Make Firebase available to our other app files later.
export {
    auth,
    db,
    onAuthStateChanged,
    signInAnonymously,
    signInWithEmailAndPassword,
    signOut,
    doc,
    getDoc,
    setDoc,
    updateDoc,
    onSnapshot,
    collection,
    addDoc,
    query,
    where,
    serverTimestamp
};

console.log("Asher's Time Bank: Firebase initialized.");
