package com.example.muyinteresanteNoTocar;

import java.util.ArrayList;

public interface iNoticiaRSS {
	void onRecibeNoticiasRSS(ArrayList<NoticiaRSS> listaNoticias);

	default void onError(DescargaNoticiasRSS.Failure failure) {
		// Existing consumers may opt into structured remote failures.
	}
}
